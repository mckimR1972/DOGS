const {HttpsError}=require('firebase-functions/v2/https');
const validId=x=>typeof x==='string' && /^[A-Za-z0-9_-]{1,128}$/.test(x);
function ids(data){
  if(!data || !validId(data.groupId) || !validId(data.photoId) || (data.dogId!=null && !validId(data.dogId)))
    throw new HttpsError('invalid-argument','사진 또는 그룹 정보가 올바르지 않아요.');
  return {gid:data.groupId,pid:data.photoId,dogId:data.dogId??null};
}
function authenticated(auth){
  if(!auth || (auth.token.firebase?.sign_in_provider==='password' && auth.token.email_verified!==true))
    throw new HttpsError('unauthenticated','로그인과 이메일 인증을 완료해 주세요.');
  return auth.uid;
}
const canDelete=(uid,leader,uploader)=>uid===leader || uid===uploader;
const photoPaths=(g,p)=>({upload:`groups/${g}/photos/${p}/upload.jpg`,image:`groups/${g}/photos/${p}/image.jpg`,thumb:`groups/${g}/photos/${p}/thumb.jpg`});
module.exports={ids,authenticated,canDelete,photoPaths};
