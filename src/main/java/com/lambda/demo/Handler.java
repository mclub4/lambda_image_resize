package com.lambda.demo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import org.springframework.beans.factory.annotation.Value;

import java.awt.*;
import java.awt.image.BufferedImage;

public class Handler implements RequestHandler<S3Event, String> {
    
    @Value("bucket-name")
    private final static String UPLOAD_BUCKET_NAME;
    

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        return "";
    }

    public BufferedImage resize(BufferedImage img, int size){
        int originalWidth = img.getWidth();
        int originalHeight = img.getHeight();

        double ratio = (double) size / Math.min(originalWidth, originalHeight);

        int newWidth = (int) (originalWidth * ratio);
        int newHeight = (int) (originalHeight * ratio);

        BufferedImage resizedImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, newWidth, newHeight, null);
        g.dispose();

        int newLength = Math.min(newWidth, newHeight);
        int x = (newWidth - newLength) / 2;
        int y = (newHeight - newLength) / 2;
        return resizedImg.getSubimage(x, y, newLength, newLength);
    }
}
