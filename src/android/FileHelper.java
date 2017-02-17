/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at
         http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package org.apache.cordova.camera;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.util.Log;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.LOG;

import java.io.*;
import java.util.Locale;

public class FileHelper {
    private static final String LOG_TAG = "FileUtils";
    private static final String _DATA = "_data";

    /**
     * Returns the real path of the given URI string.
     * If the given URI string represents a content:// URI, the real path is retrieved from the media store.
     *
     * @param uriString the URI string of the audio/image/video
     * @param cordova the current application context
     * @return the full path to the file
     */
    @SuppressWarnings("deprecation")
    public static String getRealPath(Uri uri, CordovaInterface cordova) {
        String realPath = null;
            Log.d(LOG_TAG,"getting real path");

        if (Build.VERSION.SDK_INT < 11)
            realPath = FileHelper.getRealPathFromURI_BelowAPI11(cordova.getActivity(), uri);

        // SDK >= 11
        else
            realPath = FileHelper.getRealPathFromURI_API11_And_Above(cordova.getActivity(), uri, cordova);

        //Return the URI string if no other type of path has been found
        if(realPath == null)
           realPath =  uri.toString();
 
        return realPath;
    }

    /**
     * Returns the real path of the given URI.
     * If the given URI is a content:// URI, the real path is retrieved from the media store.
     *
     * @param uri the URI of the audio/image/video
     * @param cordova the current application context
     * @return the full path to the file
     */
    public static String getRealPath(String uriString, CordovaInterface cordova) {
        return FileHelper.getRealPath(Uri.parse(uriString), cordova);
    }

    @SuppressLint("NewApi")
    public static String getRealPathFromURI_API11_And_Above(final Context context, final Uri uri, CordovaInterface cordova) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {

            Log.d(LOG_TAG,"is kitkat and docs contract");
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
            Log.d(LOG_TAG,"external storage doc");
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
            Log.d(LOG_TAG,"download provider");

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
            Log.d(LOG_TAG,"media document");
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            } else if (isDriveStorage(uri)){
                Log.d(LOG_TAG, "got to drive");
                return downloadURI(uri, cordova);
            } else{
                Log.d(LOG_TAG,"OH NO!!!");
            }

        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            Log.d(LOG_TAG,"media store");
            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            Log.d(LOG_TAG,"file");
            return uri.getPath();
        } else {

            Log.d(LOG_TAG,"ELSE crap");
        }

        return null;
    }

    public static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        String result = null;

        try {
            Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            result = cursor.getString(column_index);

        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    /**
     * Returns an input stream based on given URI string.
     *
     * @param uriString the URI string from which to obtain the input stream
     * @param cordova the current application context
     * @return an input stream into the data at the given URI or null if given an invalid URI string
     * @throws IOException
     */
    public static InputStream getInputStreamFromUriString(String uriString, CordovaInterface cordova)
            throws IOException {
        InputStream returnValue = null;
        if (uriString.startsWith("content")) {
            Uri uri = Uri.parse(uriString);
            returnValue = cordova.getActivity().getContentResolver().openInputStream(uri);
        } else if (uriString.startsWith("file://")) {
            int question = uriString.indexOf("?");
            if (question > -1) {
                uriString = uriString.substring(0, question);
            }
            if (uriString.startsWith("file:///android_asset/")) {
                Uri uri = Uri.parse(uriString);
                String relativePath = uri.getPath().substring(15);
                returnValue = cordova.getActivity().getAssets().open(relativePath);
            } else {
                // might still be content so try that first
                try {
                    returnValue = cordova.getActivity().getContentResolver().openInputStream(Uri.parse(uriString));
                } catch (Exception e) {
                    returnValue = null;
                }
                if (returnValue == null) {
                    returnValue = new FileInputStream(getRealPath(uriString, cordova));
                }
            }
        } else {
            returnValue = new FileInputStream(uriString);
        }
        return returnValue;
    }

    /**
     * Removes the "file://" prefix from the given URI string, if applicable.
     * If the given URI string doesn't have a "file://" prefix, it is returned unchanged.
     *
     * @param uriString the URI string to operate on
     * @return a path without the "file://" prefix
     */
    public static String stripFileProtocol(String uriString) {
        if (uriString.startsWith("file://")) {
            uriString = uriString.substring(7);
        }
        return uriString;
    }

    public static String getMimeTypeForExtension(String path) {
        String extension = path;
        int lastDot = extension.lastIndexOf('.');
        if (lastDot != -1) {
            extension = extension.substring(lastDot + 1);
        }
        // Convert the URI string to lower case to ensure compatibility with MimeTypeMap (see CB-2185).
        extension = extension.toLowerCase(Locale.getDefault());
        if (extension.equals("3ga")) {
            return "audio/3gpp";
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }
    
    /**
     * Returns the mime type of the data specified by the given URI string.
     *
     * @param uriString the URI string of the data
     * @return the mime type of the specified data
     */
    public static String getMimeType(String uriString, CordovaInterface cordova) {
        String mimeType = null;

        Uri uri = Uri.parse(uriString);
        if (uriString.startsWith("content://")) {
            mimeType = cordova.getActivity().getContentResolver().getType(uri);
        } else {
            mimeType = getMimeTypeForExtension(uri.getPath());
        }

        return mimeType;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     * @author paulburke
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {

                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * Create a new file from the uri. This is for files that exist outside of the device.
     * The reason for this is becasue you cant use files outside of the current context, except local files.
     *
     * @param uri The Uri to query.
     * @return The file path to the newly created file.
     * @author Samuel Thompson
     */
    public static String downloadURI(Uri uri, CordovaInterface cordova){
        Context context = cordova.getActivity();
        ParcelFileDescriptor mInputPFD;
        File newVideo = new File(context.getExternalCacheDir().getPath()+"/fileDelete");

        Log.d(LOG_TAG,uri.getPath());
        Log.d(LOG_TAG,uri.toString());
        Log.d(LOG_TAG,uri.getAuthority());

        Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
        /*
        * Get the column indexes of the data in the Cursor,
        * move to the first row in the Cursor, get the data,
        * and display it.
        */
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        String outputName = returnCursor.getString(nameIndex);
        // outputName = outputName.substring(0, outputName.lastIndexOf("."));
        Log.d(LOG_TAG,outputName);
        Log.d(LOG_TAG,Long.toString(returnCursor.getLong(sizeIndex)));
        
        try {
            /*
            * Get the content resolver instance for this context, and use it
            * to get a ParcelFileDescriptor for the file.
            */
            mInputPFD = context.getContentResolver().openFileDescriptor(uri, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Log.e("MainActivity", "File not found returning old uri good luck!");
            return uri.toString();
        }
        // Get a regular file descriptor for the file
        FileDescriptor fd = mInputPFD.getFileDescriptor();

        FileOutputStream fos = null;
        FileInputStream fis = null;

        Log.d(LOG_TAG,context.getExternalCacheDir().getPath()+"/"+outputName);
        try {
            newVideo = new File(context.getExternalCacheDir().getPath()+"/"+outputName);
            Log.d(LOG_TAG,newVideo.toURI().toString());
            Log.d(LOG_TAG,newVideo.toURI().getPath());
            Log.d(LOG_TAG,newVideo.getPath());
            Log.d(LOG_TAG,Long.toString(newVideo.getTotalSpace()));

            fos = new FileOutputStream(newVideo);

            // if file doesnt exists, then create it
            if (!newVideo.exists()) {
                Log.d(LOG_TAG,"creating file");
                newVideo.createNewFile();
            }

            fis = new FileInputStream(fd);

            Log.d(LOG_TAG,"Total file size to read (in bytes) : " + fis.available());

            int content;
            int bytesDone = 0;
            while ((content = fis.read()) != -1) {
                if(++bytesDone % 100000 == 0)
                    Log.d(LOG_TAG,"finished "+Integer.toString(bytesDone)+"bytes. "+fis.available()+" bytes to go");
                fos.write(content);
            }
            
            fos.flush();
            fos.close();

            Log.d(LOG_TAG,"done");
            Log.d(LOG_TAG,Long.toString(newVideo.getTotalSpace()));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return newVideo.toURI().toString();
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     * @author paulburke
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     * @author paulburke
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     * @author paulburke
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Drive Storage.
     */
    public static boolean isDriveStorage(Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority());
    }
}
