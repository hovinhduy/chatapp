package com.chatapp.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

public class FileUtils {

    public static boolean isValidFileType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && Arrays.asList(AppConstants.ALLOWED_FILE_TYPES).contains(contentType);
    }

    public static boolean isValidFileSize(MultipartFile file) {
        return file.getSize() <= AppConstants.MAX_FILE_SIZE;
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "";
        }
        return fileName.substring(lastIndexOf);
    }
}