/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui.Components;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;

import it.deeptelegram.messenger.AndroidUtilities;
import it.deeptelegram.messenger.BuildConfig;
import it.deeptelegram.messenger.ImageLoader;
import it.deeptelegram.messenger.MediaController;
import it.deeptelegram.tgnet.TLRPC;
import it.deeptelegram.messenger.FileLoader;
import it.deeptelegram.messenger.FileLog;
import it.deeptelegram.messenger.NotificationCenter;
import it.deeptelegram.messenger.UserConfig;
import it.deeptelegram.ui.LaunchActivity;
import it.deeptelegram.ui.PhotoAlbumPickerActivity;
import it.deeptelegram.ui.PhotoCropActivity;
import it.deeptelegram.ui.ActionBar.BaseFragment;
import it.deeptelegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;

public class AvatarUpdater implements NotificationCenter.NotificationCenterDelegate, PhotoCropActivity.PhotoEditActivityDelegate {

    public String currentPicturePath;
    private TLRPC.PhotoSize smallPhoto;
    private TLRPC.PhotoSize bigPhoto;
    public String uploadingAvatar = null;
    File picturePath = null;
    public BaseFragment parentFragment = null;
    public AvatarUpdaterDelegate delegate;
    private boolean clearAfterUpdate = false;
    public boolean returnOnly = false;

    public interface AvatarUpdaterDelegate {
        void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big);
    }

    public void clear() {
        if (uploadingAvatar != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            delegate = null;
        }
    }

    public void openCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File image = AndroidUtilities.generatePicturePath();
            if (image != null) {
                if (Build.VERSION.SDK_INT >= 24) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(parentFragment.getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", image));
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                }
                currentPicturePath = image.getAbsolutePath();
            }
            parentFragment.startActivityForResult(takePictureIntent, 13);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void openGallery() {
        if (Build.VERSION.SDK_INT >= 23 && parentFragment != null && parentFragment.getParentActivity() != null) {
            if (parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
        }
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(true, false, false, null);
        fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<String> photos, ArrayList<String> captions, ArrayList<ArrayList<TLRPC.InputDocument>> masks, ArrayList<MediaController.SearchImage> webPhotos) {
                if (!photos.isEmpty()) {
                    Bitmap bitmap = ImageLoader.loadBitmap(photos.get(0), null, 800, 800, true);
                    processBitmap(bitmap);
                }
            }

            @Override
            public void startPhotoSelectActivity() {
                try {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    photoPickerIntent.setType("image/*");
                    parentFragment.startActivityForResult(photoPickerIntent, 14);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }

            @Override
            public boolean didSelectVideo(String path) {
                return true;
            }
        });
        parentFragment.presentFragment(fragment);
    }

    private void startCrop(String path, Uri uri) {
        try {
            LaunchActivity activity = (LaunchActivity)parentFragment.getParentActivity();
            if (activity == null) {
                return;
            }
            Bundle args = new Bundle();
            if (path != null) {
                args.putString("photoPath", path);
            } else if (uri != null) {
                args.putParcelable("photoUri", uri);
            }
            PhotoCropActivity photoCropActivity = new PhotoCropActivity(args);
            photoCropActivity.setDelegate(this);
            activity.presentFragment(photoCropActivity);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            Bitmap bitmap = ImageLoader.loadBitmap(path, uri, 800, 800, true);
            processBitmap(bitmap);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 13) {
                PhotoViewer.getInstance().setParentActivity(parentFragment.getParentActivity());
                int orientation = 0;
                try {
                    ExifInterface ei = new ExifInterface(currentPicturePath);
                    int exif = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    switch(exif) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            orientation = 90;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            orientation = 180;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            orientation = 270;
                            break;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                final ArrayList<Object> arrayList = new ArrayList<>();
                arrayList.add(new MediaController.PhotoEntry(0, 0, 0, currentPicturePath, orientation, false));
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, 1, new PhotoViewer.EmptyPhotoViewerProvider() {
                    @Override
                    public void sendButtonPressed(int index) {
                        String path = null;
                        MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) arrayList.get(0);
                        if (photoEntry.imagePath != null) {
                            path = photoEntry.imagePath;
                        } else if (photoEntry.path != null) {
                            path = photoEntry.path;
                        }
                        Bitmap bitmap = ImageLoader.loadBitmap(path, null, 800, 800, true);
                        processBitmap(bitmap);
                    }

                    @Override
                    public boolean allowCaption() {
                        return false;
                    }
                }, null);
                AndroidUtilities.addMediaToGallery(currentPicturePath);
                currentPicturePath = null;
            } else if (requestCode == 14) {
                if (data == null || data.getData() == null) {
                    return;
                }
                startCrop(null, data.getData());
            }
        }
    }

    private void processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 100, 100, 80, false);
        bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
        bitmap.recycle();
        if (bigPhoto != null && smallPhoto != null) {
            if (returnOnly) {
                if (delegate != null) {
                    delegate.didUploadedPhoto(null, smallPhoto, bigPhoto);
                }
            } else {
                UserConfig.saveConfig(false);
                uploadingAvatar = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
                NotificationCenter.getInstance().addObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().addObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                FileLoader.getInstance().uploadFile(uploadingAvatar, false, true);
            }
        }
    }

    @Override
    public void didFinishEdit(Bitmap bitmap) {
        processBitmap(bitmap);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            String location = (String)args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                if (delegate != null) {
                    delegate.didUploadedPhoto((TLRPC.InputFile)args[1], smallPhoto, bigPhoto);
                }
                uploadingAvatar = null;
                if (clearAfterUpdate) {
                    parentFragment = null;
                    delegate = null;
                }
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            String location = (String)args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                uploadingAvatar = null;
                if (clearAfterUpdate) {
                    parentFragment = null;
                    delegate = null;
                }
            }
        }
    }
}
