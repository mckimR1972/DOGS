const {test}=require('node:test');const assert=require('node:assert/strict');
const {ids,authenticated,canDelete,photoPaths}=require('../policy');
test('common tag maps to null; one dog ID accepted',()=>{assert.equal(ids({groupId:'g',photoId:'p'}).dogId,null);assert.equal(ids({groupId:'g',photoId:'p',dogId:'dog'}).dogId,'dog');});
test('multiple tags and traversal rejected',()=>{for(const data of [{groupId:'g',photoId:'../p'},{groupId:'g',photoId:'p',dogId:['a','b']}])assert.throws(()=>ids(data));});
test('unverified password users rejected; verified and Google users accepted',()=>{assert.throws(()=>authenticated(null));assert.throws(()=>authenticated({uid:'a',token:{firebase:{sign_in_provider:'password'},email_verified:false}}));assert.equal(authenticated({uid:'a',token:{firebase:{sign_in_provider:'password'},email_verified:true}}),'a');assert.equal(authenticated({uid:'a',token:{firebase:{sign_in_provider:'google.com'}}}),'a');});
test('deletion requires uploader or leader, on top of caller membership check',()=>{assert.ok(canDelete('a','leader','a'));assert.ok(canDelete('leader','leader','a'));assert.equal(canDelete('b','leader','a'),false);});
test('each photo gets separate private staging and publication paths',()=>{assert.deepEqual(photoPaths('g','p'),{upload:'groups/g/photos/p/upload.jpg',image:'groups/g/photos/p/image.jpg',thumb:'groups/g/photos/p/thumb.jpg'});});
