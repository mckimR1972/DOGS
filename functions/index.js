const {initializeApp, getApps} = require('firebase-admin/app');
const {getFirestore, FieldValue, Timestamp} = require('firebase-admin/firestore');
const {getStorage} = require('firebase-admin/storage');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {onSchedule} = require('firebase-functions/v2/scheduler');
const {onObjectFinalized} = require('firebase-functions/v2/storage');
const sharp = require('sharp');
if (!getApps().length) initializeApp();
const db = getFirestore();
const region = 'asia-northeast3';
const bucketName = process.env.PHOTO_BUCKET || `${process.env.GCLOUD_PROJECT || 'dogs-dev-f9a4e'}.firebasestorage.app`;
const bucket = () => getStorage().bucket(bucketName);
const options = {region, maxInstances: 2, memory: '512MiB', timeoutSeconds: 120};
const {ids, authenticated, canDelete, photoPaths} = require('./policy');
function checked(request) {
  const uid = authenticated(request.auth);
  return {...ids(request.data), uid};
}
async function membership(tx, gid, uid) {
  const ref = db.doc(`groups/${gid}`);
  const [group, member] = await tx.getAll(ref, ref.collection('members').doc(uid));
  if (!group.exists || !member.exists) throw new HttpsError('permission-denied', '현재 그룹 구성원만 사용할 수 있어요.');
  return group.data();
}
const refFor = (gid, pid) => db.doc(`groups/${gid}/photos/${pid}`);
const stamp = () => FieldValue.serverTimestamp();
const expires = () => Timestamp.fromMillis(Date.now()+24*60*60*1000);
async function purgeFiles(gid, pid) {
  for (const path of Object.values(photoPaths(gid, pid))) await bucket().file(path).delete({ignoreNotFound: true});
}
exports.preparePhotoUpload = onCall(options, async request => {
  const {gid,pid,uid,dogId} = checked(request);
  const result = await db.runTransaction(async tx => {
    await membership(tx,gid,uid);
    const ref = refFor(gid,pid), existing = await tx.get(ref);
    if (existing.exists) {
      const old = existing.data();
      if (old.uploader_id !== uid || old.dog_id !== dogId) throw new HttpsError('permission-denied','다른 업로드 작업이에요.');
      if (!['uploading','ready'].includes(old.status)) throw new HttpsError('failed-precondition','종료된 업로드예요. 새 사진을 선택해 주세요.');
      return {status:old.status, upload_path:photoPaths(gid,pid).upload};
    }
    if (dogId && !(await tx.get(db.doc(`groups/${gid}/dogs/${dogId}`))).exists) throw new HttpsError('invalid-argument','반려견 소속을 확인해 주세요.');
    const paths = photoPaths(gid,pid);
    tx.create(ref,{id:pid,group_id:gid,dog_id:dogId,uploader_id:uid,url:'',storage_path:paths.image,
      thumbnail_path:paths.thumb,status:'uploading',created_at:stamp(),updated_at:stamp(),cleanup_at:expires()});
    return {status:'uploading', upload_path:paths.upload};
  });
  const [exists] = result.status === 'ready' ? [true] : await bucket().file(result.upload_path).exists();
  return {...result, upload_exists: exists};
});
exports.completePhotoUpload = onCall({...options, concurrency:1}, async request => {
  const {gid,pid,uid} = checked(request), ref=refFor(gid,pid), paths=photoPaths(gid,pid);
  const alreadyReady = await db.runTransaction(async tx => {
    await membership(tx,gid,uid);
    const snap=await tx.get(ref), photo=snap.data();
    if (!photo || photo.uploader_id !== uid) throw new HttpsError('permission-denied','업로드 권한이 없어요.');
    if (photo.status==='ready') return true;
    if(photo.status!=='uploading') throw new HttpsError('failed-precondition','종료된 업로드예요.');
    // Keep uploads create-only. The immutable staging object makes retry deterministic.
    tx.update(ref,{updated_at:stamp(),cleanup_at:expires()});
    return false;
  });
  if(alreadyReady) return {status:'ready'};
  const file=bucket().file(paths.upload);
  let image,thumb;
  try {
    const [meta]=await file.getMetadata();
    if(meta.contentType!=='image/jpeg' || Number(meta.size)>5*1024*1024) throw Error('invalid-image');
    const [input]=await file.download({validation:'crc32c'});
    const decoded=sharp(input,{limitInputPixels:24_000_000,failOn:'error'});
    const m=await decoded.metadata();
    if(m.format!=='jpeg' || !m.width || !m.height) throw Error('invalid-image');
    // Re-encode to remove metadata and never publish the user-supplied bytes as-is.
    image=await decoded.rotate().resize({width:2048,height:2048,fit:'inside',withoutEnlargement:true}).jpeg({quality:80}).toBuffer();
    thumb=await sharp(image).resize({width:400,height:400,fit:'inside',withoutEnlargement:true}).jpeg({quality:75}).toBuffer();
    if(image.length>5*1024*1024 || thumb.length>500*1024) throw Error('invalid-image');
  } catch(e) {
    if(e.code===404) throw new HttpsError('failed-precondition','사진 업로드를 먼저 완료해 주세요.');
    if(e.message==='invalid-image' || /image|jpeg|corrupt|pixel|unsupported/i.test(e.message))
      throw new HttpsError('invalid-argument','올바른 JPEG 사진을 선택해 주세요.');
    throw new HttpsError('unavailable','사진을 확인하지 못했어요. 다시 시도해 주세요.');
  }
  // No Firebase download tokens on published files. Clients use authenticated SDK reads.
  await bucket().file(paths.image).save(image,{resumable:false,metadata:{contentType:'image/jpeg',cacheControl:'private,max-age=0'}});
  await bucket().file(paths.thumb).save(thumb,{resumable:false,metadata:{contentType:'image/jpeg',cacheControl:'private,max-age=0'}});
  try {
    await db.runTransaction(async tx => {
      await membership(tx,gid,uid);
      const p=(await tx.get(ref)).data();
      if(!p || p.uploader_id!==uid || !['uploading','ready'].includes(p.status)) throw new HttpsError('failed-precondition','취소되거나 삭제된 사진이에요.');
      tx.update(ref,{status:'ready',updated_at:stamp(),size_bytes:image.length});
    });
  } catch(e) {
    // Deletion racing finalization must not leave published files behind.
    const p=(await ref.get()).data();
    if(!p || ['deleting','deleted'].includes(p.status)) await purgeFiles(gid,pid);
    throw e;
  }
  // Retain staging until scheduled cleanup so an overlapping retry can finish safely.
  return {status:'ready'};
});
exports.deletePhoto = onCall(options, async request => {
  const {gid,pid,uid}=checked(request), ref=refFor(gid,pid);
  await db.runTransaction(async tx=>{
    const group=await membership(tx,gid,uid), p=(await tx.get(ref)).data();
    if(!p) return;
    if(!canDelete(uid,group.leader_id,p.uploader_id)) throw new HttpsError('permission-denied','올린 사람 또는 그룹장만 삭제할 수 있어요.');
    tx.update(ref,{status:'deleting',updated_at:stamp(),cleanup_at:Timestamp.now()});
  });
  await purgeFiles(gid,pid);
  await db.runTransaction(async tx=>{if((await tx.get(ref)).exists) tx.update(ref,{status:'deleted',updated_at:stamp(),cleanup_at:FieldValue.delete()});});
  return {status:'deleted'};
});
// A late resumable upload / finalizer write after deletion is removed too.
exports.cleanLatePhotoFile = onObjectFinalized({...options,bucket:bucketName},async event=>{
  const match=/^groups\/([^/]+)\/photos\/([^/]+)\/(upload|image|thumb)\.jpg$/.exec(event.data.name);
  if(!match) return;
  const p=(await refFor(match[1],match[2]).get()).data();
  if(!p || ['deleting','deleted'].includes(p.status))
    await bucket().file(event.data.name,{generation:event.data.generation}).delete({ignoreNotFound:true});
});
exports.cleanAbandonedPhotos = onSchedule({...options,schedule:'every 24 hours',timeZone:'Asia/Seoul'},async()=>{
  const cutoff=Timestamp.now();
  const stale=await db.collectionGroup('photos').where('cleanup_at','<=',cutoff).orderBy('cleanup_at').limit(200).get();
  for(const doc of stale.docs){
    const gid=doc.ref.parent.parent?.id,pid=doc.id;
    if(!gid || doc.ref.path!==`groups/${gid}/photos/${pid}`) continue;
    const action=await db.runTransaction(async tx=>{
      const p=(await tx.get(doc.ref)).data();
      if(!p?.cleanup_at || p.cleanup_at.toMillis()>cutoff.toMillis()) return 'skip';
      if(p.status==='ready') return 'staging';
      tx.update(doc.ref,{status:'deleting',updated_at:stamp()}); return 'all';
    });
    if(action==='staging') {
      await bucket().file(photoPaths(gid,pid).upload).delete({ignoreNotFound:true});
      await db.runTransaction(async tx=>{
        if((await tx.get(doc.ref)).get('status')==='ready') tx.update(doc.ref,{cleanup_at:FieldValue.delete()});
      });
    }
    if(action==='all') {
      await purgeFiles(gid,pid);
      await doc.ref.update({status:'deleted',updated_at:stamp(),cleanup_at:FieldValue.delete()});
    }
  }
});
