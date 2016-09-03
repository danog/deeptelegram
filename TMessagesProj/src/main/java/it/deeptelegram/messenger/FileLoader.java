/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class FileLoader {

    public interface FileLoaderDelegate {
        void fileUploadProgressChanged(String location, float progress, boolean isEncrypted);

        void fileDidUploaded(String location, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv, long totalFileSize);

        void fileDidFailedUpload(String location, boolean isEncrypted);

        void fileDidLoaded(String location, File finalFile, int type);

        void fileDidFailedLoad(String location, int state);

        void fileLoadProgressChanged(String location, float progress);
    }

    public static final int MEDIA_DIR_IMAGE = 0;
    public static final int MEDIA_DIR_AUDIO = 1;
    public static final int MEDIA_DIR_VIDEO = 2;
    public static final int MEDIA_DIR_DOCUMENT = 3;
    public static final int MEDIA_DIR_CACHE = 4;

    private HashMap<Integer, File> mediaDirs = null;
    private volatile DispatchQueue fileLoaderQueue = new DispatchQueue("fileUploadQueue");

    private LinkedList<FileUploadOperation> uploadOperationQueue = new LinkedList<>();
    private LinkedList<FileUploadOperation> uploadSmallOperationQueue = new LinkedList<>();
    private LinkedList<FileLoadOperation> loadOperationQueue = new LinkedList<>();
    private LinkedList<FileLoadOperation> audioLoadOperationQueue = new LinkedList<>();
    private LinkedList<FileLoadOperation> photoLoadOperationQueue = new LinkedList<>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPaths = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FileUploadOperation> uploadOperationPathsEnc = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FileLoadOperation> loadOperationPaths = new ConcurrentHashMap<>();
    private HashMap<String, Long> uploadSizes = new HashMap<>();

    private FileLoaderDelegate delegate = null;

    private int currentLoadOperationsCount = 0;
    private int currentAudioLoadOperationsCount = 0;
    private int currentPhotoLoadOperationsCount = 0;
    private int currentUploadOperationsCount = 0;
    private int currentUploadSmallOperationsCount = 0;

    private static volatile FileLoader Instance = null;

    public static FileLoader getInstance() {
        FileLoader localInstance = Instance;
        if (localInstance == null) {
            synchronized (FileLoader.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new FileLoader();
                }
            }
        }
        return localInstance;
    }

    public void setMediaDirs(HashMap<Integer, File> dirs) {
        mediaDirs = dirs;
    }

    public File checkDirectory(int type) {
        return mediaDirs.get(type);
    }

    public File getDirectory(int type) {
        File dir = mediaDirs.get(type);
        if (dir == null && type != MEDIA_DIR_CACHE) {
            dir = mediaDirs.get(MEDIA_DIR_CACHE);
        }
        try {
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            //don't promt
        }
        return dir;
    }

    public void cancelUploadFile(final String location, final boolean enc) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileUploadOperation operation;
                if (!enc) {
                    operation = uploadOperationPaths.get(location);
                } else {
                    operation = uploadOperationPathsEnc.get(location);
                }
                uploadSizes.remove(location);
                if (operation != null) {
                    uploadOperationPathsEnc.remove(location);
                    uploadOperationQueue.remove(operation);
                    uploadSmallOperationQueue.remove(operation);
                    operation.cancel();
                }
            }
        });
    }

    public void checkUploadNewDataAvailable(final String location, final boolean encrypted, final long finalSize) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileUploadOperation operation;
                if (encrypted) {
                    operation = uploadOperationPathsEnc.get(location);
                } else {
                    operation = uploadOperationPaths.get(location);
                }
                if (operation != null) {
                    operation.checkNewDataAvailable(finalSize);
                } else if (finalSize != 0) {
                    uploadSizes.put(location, finalSize);
                }
            }
        });
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small) {
        uploadFile(location, encrypted, small, 0);
    }

    public void uploadFile(final String location, final boolean encrypted, final boolean small, final int estimatedSize) {
        if (location == null) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (encrypted) {
                    if (uploadOperationPathsEnc.containsKey(location)) {
                        return;
                    }
                } else {
                    if (uploadOperationPaths.containsKey(location)) {
                        return;
                    }
                }
                int esimated = estimatedSize;
                if (esimated != 0) {
                    Long finalSize = uploadSizes.get(location);
                    if (finalSize != null) {
                        esimated = 0;
                        uploadSizes.remove(location);
                    }
                }
                FileUploadOperation operation = new FileUploadOperation(location, encrypted, esimated);
                if (encrypted) {
                    uploadOperationPathsEnc.put(location, operation);
                } else {
                    uploadOperationPaths.put(location, operation);
                }
                operation.delegate = new FileUploadOperation.FileUploadOperationDelegate() {
                    @Override
                    public void didFinishUploadingFile(final FileUploadOperation operation, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile, final byte[] key, final byte[] iv) {
                        fileLoaderQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                if (encrypted) {
                                    uploadOperationPathsEnc.remove(location);
                                } else {
                                    uploadOperationPaths.remove(location);
                                }
                                if (small) {
                                    currentUploadSmallOperationsCount--;
                                    if (currentUploadSmallOperationsCount < 1) {
                                        FileUploadOperation operation = uploadSmallOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadSmallOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentUploadOperationsCount--;
                                    if (currentUploadOperationsCount < 1) {
                                        FileUploadOperation operation = uploadOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                }
                                if (delegate != null) {
                                    delegate.fileDidUploaded(location, inputFile, inputEncryptedFile, key, iv, operation.getTotalFileSize());
                                }
                            }
                        });
                    }

                    @Override
                    public void didFailedUploadingFile(final FileUploadOperation operation) {
                        fileLoaderQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                if (encrypted) {
                                    uploadOperationPathsEnc.remove(location);
                                } else {
                                    uploadOperationPaths.remove(location);
                                }
                                if (delegate != null) {
                                    delegate.fileDidFailedUpload(location, encrypted);
                                }
                                if (small) {
                                    currentUploadSmallOperationsCount--;
                                    if (currentUploadSmallOperationsCount < 1) {
                                        FileUploadOperation operation = uploadSmallOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadSmallOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                } else {
                                    currentUploadOperationsCount--;
                                    if (currentUploadOperationsCount < 1) {
                                        FileUploadOperation operation = uploadOperationQueue.poll();
                                        if (operation != null) {
                                            currentUploadOperationsCount++;
                                            operation.start();
                                        }
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void didChangedUploadProgress(FileUploadOperation operation, final float progress) {
                        if (delegate != null) {
                            delegate.fileUploadProgressChanged(location, progress, encrypted);
                        }
                    }
                };
                if (small) {
                    if (currentUploadSmallOperationsCount < 1) {
                        currentUploadSmallOperationsCount++;
                        operation.start();
                    } else {
                        uploadSmallOperationQueue.add(operation);
                    }
                } else {
                    if (currentUploadOperationsCount < 1) {
                        currentUploadOperationsCount++;
                        operation.start();
                    } else {
                        uploadOperationQueue.add(operation);
                    }
                }
            }
        });
    }

    public void cancelLoadFile(TLRPC.Document document) {
        cancelLoadFile(document, null, null);
    }

    public void cancelLoadFile(TLRPC.PhotoSize photo) {
        cancelLoadFile(null, photo.location, null);
    }

    public void cancelLoadFile(TLRPC.FileLocation location, String ext) {
        cancelLoadFile(null, location, ext);
    }

    private void cancelLoadFile(final TLRPC.Document document, final TLRPC.FileLocation location, final String locationExt) {
        if (location == null && document == null) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String fileName = null;
                if (location != null) {
                    fileName = getAttachFileName(location, locationExt);
                } else if (document != null) {
                    fileName = getAttachFileName(document);
                }
                if (fileName == null) {
                    return;
                }
                FileLoadOperation operation = loadOperationPaths.remove(fileName);
                if (operation != null) {
                    if (MessageObject.isVoiceDocument(document)) {
                        audioLoadOperationQueue.remove(operation);
                    } else if (location != null) {
                        photoLoadOperationQueue.remove(operation);
                    } else {
                        loadOperationQueue.remove(operation);
                    }
                    operation.cancel();
                }
            }
        });
    }

    public boolean isLoadingFile(final String fileName) {
        final Semaphore semaphore = new Semaphore(0);
        final Boolean[] result = new Boolean[1];
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                result[0] = loadOperationPaths.containsKey(fileName);
                semaphore.release();
            }
        });
        try {
            semaphore.acquire();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return result[0];
    }

    public void loadFile(TLRPC.PhotoSize photo, String ext, boolean cacheOnly) {
        loadFile(null, photo.location, ext, photo.size, false, cacheOnly || (photo != null && photo.size == 0 || photo.location.key != null));
    }

    public void loadFile(TLRPC.Document document, boolean force, boolean cacheOnly) {
        loadFile(document, null, null, 0, force, cacheOnly || document != null && document.key != null);
    }

    public void loadFile(TLRPC.FileLocation location, String ext, int size, boolean cacheOnly) {
        loadFile(null, location, ext, size, true, cacheOnly || size == 0 || (location != null && location.key != null));
    }

    private void loadFile(final TLRPC.Document document, final TLRPC.FileLocation location, final String locationExt, final int locationSize, final boolean force, final boolean cacheOnly) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String fileName = null;
                if (location != null) {
                    fileName = getAttachFileName(location, locationExt);
                } else if (document != null) {
                    fileName = getAttachFileName(document);
                }
                if (fileName == null || fileName.contains("" + Integer.MIN_VALUE)) {
                    return;
                }

                FileLoadOperation operation;
                operation = loadOperationPaths.get(fileName);
                if (operation != null) {
                    if (force) {
                        LinkedList<FileLoadOperation> downloadQueue;
                        if (MessageObject.isVoiceDocument(document)) {
                            downloadQueue = audioLoadOperationQueue;
                        } else if (location != null) {
                            downloadQueue = photoLoadOperationQueue;
                        } else {
                            downloadQueue = loadOperationQueue;
                        }
                        if (downloadQueue != null) {
                            int index = downloadQueue.indexOf(operation);
                            if (index != -1) {
                                downloadQueue.remove(index);
                                downloadQueue.add(0, operation);
                                operation.setForceRequest(true);
                            }
                        }
                    }
                    return;
                }

                File tempDir = getDirectory(MEDIA_DIR_CACHE);
                File storeDir = tempDir;
                int type = MEDIA_DIR_CACHE;

                if (location != null) {
                    operation = new FileLoadOperation(location, locationExt, locationSize);
                    type = MEDIA_DIR_IMAGE;
                } else if (document != null) {
                    operation = new FileLoadOperation(document);
                    if (MessageObject.isVoiceDocument(document)) {
                        type = MEDIA_DIR_AUDIO;
                    } else if (MessageObject.isVideoDocument(document)) {
                        type = MEDIA_DIR_VIDEO;
                    } else {
                        type = MEDIA_DIR_DOCUMENT;
                    }
                }
                if (!cacheOnly) {
                    storeDir = getDirectory(type);
                }
                operation.setPaths(storeDir, tempDir);

                final String finalFileName = fileName;
                final int finalType = type;
                loadOperationPaths.put(fileName, operation);
                operation.setDelegate(new FileLoadOperation.FileLoadOperationDelegate() {
                    @Override
                    public void didFinishLoadingFile(FileLoadOperation operation, File finalFile) {
                        if (delegate != null) {
                            delegate.fileDidLoaded(finalFileName, finalFile, finalType);
                        }
                        checkDownloadQueue(document, location, finalFileName);
                    }

                    @Override
                    public void didFailedLoadingFile(FileLoadOperation operation, int canceled) {
                        checkDownloadQueue(document, location, finalFileName);
                        if (delegate != null) {
                            delegate.fileDidFailedLoad(finalFileName, canceled);
                        }
                    }

                    @Override
                    public void didChangedLoadProgress(FileLoadOperation operation, float progress) {
                        if (delegate != null) {
                            delegate.fileLoadProgressChanged(finalFileName, progress);
                        }
                    }
                });
                int maxCount = force ? 3 : 1;
                if (type == MEDIA_DIR_AUDIO) {
                    if (currentAudioLoadOperationsCount < maxCount) {
                        currentAudioLoadOperationsCount++;
                        operation.start();
                    } else {
                        if (force) {
                            audioLoadOperationQueue.add(0, operation);
                        } else {
                            audioLoadOperationQueue.add(operation);
                        }
                    }
                } else if (location != null) {
                    if (currentPhotoLoadOperationsCount < maxCount) {
                        currentPhotoLoadOperationsCount++;
                        operation.start();
                    } else {
                        if (force) {
                            photoLoadOperationQueue.add(0, operation);
                        } else {
                            photoLoadOperationQueue.add(operation);
                        }
                    }
                } else {
                    if (currentLoadOperationsCount < maxCount) {
                        currentLoadOperationsCount++;
                        operation.start();
                    } else {
                        if (force) {
                            loadOperationQueue.add(0, operation);
                        } else {
                            loadOperationQueue.add(operation);
                        }
                    }
                }
            }
        });
    }

    private void checkDownloadQueue(final TLRPC.Document document, final TLRPC.FileLocation location, final String arg1) {
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                loadOperationPaths.remove(arg1);
                FileLoadOperation operation;
                if (MessageObject.isVoiceDocument(document)) {
                    currentAudioLoadOperationsCount--;
                    if (!audioLoadOperationQueue.isEmpty()) {
                        operation = audioLoadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (currentAudioLoadOperationsCount < maxCount) {
                            operation = audioLoadOperationQueue.poll();
                            if (operation != null) {
                                currentAudioLoadOperationsCount++;
                                operation.start();
                            }
                        }
                    }
                } else if (location != null) {
                    currentPhotoLoadOperationsCount--;
                    if (!photoLoadOperationQueue.isEmpty()) {
                        operation = photoLoadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (currentPhotoLoadOperationsCount < maxCount) {
                            operation = photoLoadOperationQueue.poll();
                            if (operation != null) {
                                currentPhotoLoadOperationsCount++;
                                operation.start();
                            }
                        }
                    }
                } else {
                    currentLoadOperationsCount--;
                    if (!loadOperationQueue.isEmpty()) {
                        operation = loadOperationQueue.get(0);
                        int maxCount = operation.isForceRequest() ? 3 : 1;
                        if (currentLoadOperationsCount < maxCount) {
                            operation = loadOperationQueue.poll();
                            if (operation != null) {
                                currentLoadOperationsCount++;
                                operation.start();
                            }
                        }
                    }
                }
            }
        });
    }

    public void setDelegate(FileLoaderDelegate delegate) {
        this.delegate = delegate;
    }

    public static String getMessageFileName(TLRPC.Message message) {
        if (message == null) {
            return "";
        }
        if (message instanceof TLRPC.TL_messageService) {
            if (message.action.photo != null) {
                ArrayList<TLRPC.PhotoSize> sizes = message.action.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getAttachFileName(sizeFull);
                    }
                }
            }
        } else {
            if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                return getAttachFileName(message.media.document);
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = message.media.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getAttachFileName(sizeFull);
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (message.media.webpage.photo != null) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.media.webpage.photo.sizes;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                        if (sizeFull != null) {
                            return getAttachFileName(sizeFull);
                        }
                    }
                } else if (message.media.webpage.document != null) {
                    return getAttachFileName(message.media.webpage.document);
                }
            }
        }
        return "";
    }

    public static File getPathToMessage(TLRPC.Message message) {
        if (message == null) {
            return new File("");
        }
        if (message instanceof TLRPC.TL_messageService) {
            if (message.action.photo != null) {
                ArrayList<TLRPC.PhotoSize> sizes = message.action.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull);
                    }
                }
            }
        } else {
            if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                return getPathToAttach(message.media.document);
            } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = message.media.photo.sizes;
                if (sizes.size() > 0) {
                    TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getPathToAttach(sizeFull);
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                if (message.media.webpage.document != null) {
                    return getPathToAttach(message.media.webpage.document);
                } else if (message.media.webpage.photo != null) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.media.webpage.photo.sizes;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize());
                        if (sizeFull != null) {
                            return getPathToAttach(sizeFull);
                        }
                    }
                }
            }
        }
        return new File("");
    }

    public static File getPathToAttach(TLObject attach) {
        return getPathToAttach(attach, null, false);
    }

    public static File getPathToAttach(TLObject attach, boolean forceCache) {
        return getPathToAttach(attach, null, forceCache);
    }

    public static File getPathToAttach(TLObject attach, String ext, boolean forceCache) {
        File dir = null;
        if (forceCache) {
            dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
        } else {
            if (attach instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) attach;
                if (document.key != null) {
                    dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
                } else {
                    if (MessageObject.isVoiceDocument(document)) {
                        dir = getInstance().getDirectory(MEDIA_DIR_AUDIO);
                    } else if (MessageObject.isVideoDocument(document)) {
                        dir = getInstance().getDirectory(MEDIA_DIR_VIDEO);
                    } else {
                        dir = getInstance().getDirectory(MEDIA_DIR_DOCUMENT);
                    }
                }
            } else if (attach instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize photoSize = (TLRPC.PhotoSize) attach;
                if (photoSize.location == null || photoSize.location.key != null || photoSize.location.volume_id == Integer.MIN_VALUE && photoSize.location.local_id < 0 || photoSize.size < 0) {
                    dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
                } else {
                    dir = getInstance().getDirectory(MEDIA_DIR_IMAGE);
                }
            } else if (attach instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation fileLocation = (TLRPC.FileLocation) attach;
                if (fileLocation.key != null || fileLocation.volume_id == Integer.MIN_VALUE && fileLocation.local_id < 0) {
                    dir = getInstance().getDirectory(MEDIA_DIR_CACHE);
                } else {
                    dir = getInstance().getDirectory(MEDIA_DIR_IMAGE);
                }
            }
        }
        if (dir == null) {
            return new File("");
        }
        return new File(dir, getAttachFileName(attach, ext));
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side) {
        return getClosestPhotoSizeWithSize(sizes, side, false);
    }

    public static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> sizes, int side, boolean byMinSide) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        int lastSide = 0;
        TLRPC.PhotoSize closestObject = null;
        for (int a = 0; a < sizes.size(); a++) {
            TLRPC.PhotoSize obj = sizes.get(a);
            if (obj == null) {
                continue;
            }
            if (byMinSide) {
                int currentSide = obj.h >= obj.w ? obj.w : obj.h;
                if (closestObject == null || side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || side > lastSide && lastSide < currentSide) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            } else {
                int currentSide = obj.w >= obj.h ? obj.w : obj.h;
                if (closestObject == null || side > 100 && closestObject.location != null && closestObject.location.dc_id == Integer.MIN_VALUE || obj instanceof TLRPC.TL_photoCachedSize || currentSide <= side && lastSide < currentSide) {
                    closestObject = obj;
                    lastSide = currentSide;
                }
            }
        }
        return closestObject;
    }

    public static String getFileExtension(File file) {
        String name = file.getName();
        try {
            return name.substring(name.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDocumentFileName(TLRPC.Document document) {
        if (document != null) {
            if (document.file_name != null) {
                return document.file_name;
            }
            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute documentAttribute = document.attributes.get(a);
                if (documentAttribute instanceof TLRPC.TL_documentAttributeFilename) {
                    return documentAttribute.file_name;
                }
            }
        }
        return "";
    }

    public static String getDocumentExtension(TLRPC.Document document) {
        String fileName = getDocumentFileName(document);
        int idx = fileName.lastIndexOf('.');
        String ext = null;
        if (idx != -1) {
            ext = fileName.substring(idx + 1);
        }
        if (ext == null || ext.length() == 0) {
            ext = document.mime_type;
        }
        if (ext == null) {
            ext = "";
        }
        ext = ext.toUpperCase();
        return ext;
    }

    public static String getAttachFileName(TLObject attach) {
        return getAttachFileName(attach, null);
    }

    public static String getAttachFileName(TLObject attach, String ext) {
        if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) attach;
            String docExt = null;
            if (docExt == null) {
                docExt = getDocumentFileName(document);
                int idx;
                if (docExt == null || (idx = docExt.lastIndexOf('.')) == -1) {
                    docExt = "";
                } else {
                    docExt = docExt.substring(idx);
                }
            }
            if (docExt.length() <= 1) {
                if (document.mime_type != null) {
                    switch (document.mime_type) {
                        case "video/mp4":
                            docExt = ".mp4";
                            break;
                        case "audio/ogg":
                            docExt = ".ogg";
                            break;
                        default:
                            docExt = "";
                            break;
                    }
                } else {
                    docExt = "";
                }
            }
            if (docExt.length() > 1) {
                return document.dc_id + "_" + document.id + docExt;
            } else {
                return document.dc_id + "_" + document.id;
            }
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photo = (TLRPC.PhotoSize) attach;
            if (photo.location == null || photo.location instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            return photo.location.volume_id + "_" + photo.location.local_id + "." + (ext != null ? ext : "jpg");
        } else if (attach instanceof TLRPC.FileLocation) {
            if (attach instanceof TLRPC.TL_fileLocationUnavailable) {
                return "";
            }
            TLRPC.FileLocation location = (TLRPC.FileLocation) attach;
            return location.volume_id + "_" + location.local_id + "." + (ext != null ? ext : "jpg");
        }
        return "";
    }

    public void deleteFiles(final ArrayList<File> files, final int type) {
        if (files == null || files.isEmpty()) {
            return;
        }
        fileLoaderQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                for (int a = 0; a < files.size(); a++) {
                    File file = files.get(a);
                    if (file.exists()) {
                        try {
                            if (!file.delete()) {
                                file.deleteOnExit();
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                    try {
                        File qFile = new File(file.getParentFile(), "q_" + file.getName());
                        if (qFile.exists()) {
                            if (!qFile.delete()) {
                                qFile.deleteOnExit();
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
                if (type == 2) {
                    ImageLoader.getInstance().clearMemory();
                }
            }
        });
    }
}
