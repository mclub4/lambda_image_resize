package com.lambda.demo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Handler implements RequestHandler<S3Event, String> {

    private final String baseType = "image/";
    private final int OUTPUT_SIZE = 400;

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log(s3Event.getRecords().size() + " Images Uploaded Event Accepted \n");

        S3Client s3Client = null;
        InputStream inputStream = null;

        try{
            S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);
            String srcBucket = record.getS3().getBucket().getName();
            String srcKey = record.getS3().getObject().getUrlDecodedKey();

            Matcher matcher = Pattern.compile("(.+/)*(.+)(\\..+)$").matcher(srcKey);
            if (!matcher.matches()) {
                logger.log("Unable to infer image type for key " + srcKey);
                return "";
            }

            String path = matcher.group(1);
            String fileName = matcher.group(2);
            String imgType = matcher.group(3);

            final List<String> allowedTypes = List.of(".jpg", ".jpeg", ".png");
            if(!allowedTypes.contains(imgType)){
                logger.log(fileName + " has unsupported image type " + imgType);
                return "";
            }

            s3Client = S3Client.builder().build();
            inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(srcBucket)
                    .key(srcKey)
                    .build());

            BufferedImage srcImage = ImageIO.read(inputStream);

            BufferedImage newImage = resize(srcImage, OUTPUT_SIZE);

            String dstBucket = srcBucket + "-resized";

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(newImage, imgType.substring(1), outputStream);

            String contentType = baseType + imgType.substring(1);

            Map<String, String> metadata = new HashMap<String, String>();
            metadata.put("Content-Length", Integer.toString(outputStream.size()));
            metadata.put("Content-Type", contentType);


            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(dstBucket)
                    .key(srcKey)
                    .metadata(metadata)
                    .build();

            logger.log("Writing to: " + dstBucket + "/" + srcKey);
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(outputStream.toByteArray()));
            logger.log("Successfully resized " + srcBucket + "/" + srcKey + "and uploaded to " + dstBucket + "/" + srcKey);

            logger.log("Deleting from: " + srcBucket + "/" + srcKey);
            s3Client.deleteObject(builder -> builder.bucket(srcBucket).key(srcKey));

            return "Ok";
        } catch (Exception e) {
            logger.log(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    logger.log("Error closing inputStream: " + e.getMessage());
                }
            }
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }

    public BufferedImage resize(BufferedImage img, int size) {
        int originalWidth = img.getWidth();
        int originalHeight = img.getHeight();

        int newWidth, newHeight;
        if (originalWidth > originalHeight) {
            newWidth = size;
            newHeight = (newWidth * originalHeight) / originalWidth;
        } else {
            newHeight = size;
            newWidth = (newHeight * originalWidth) / originalHeight;
        }

        BufferedImage resizedImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImg.createGraphics();
        g.setPaint(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resizedImg;
    }
}
