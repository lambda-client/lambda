package me.zeroeightsix.kami.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.BufferUtils.*;


/**
 * Updated by S-B99 on 07/02/20
 */
public class WindowIcon {
//    public static ByteBuffer[] ExtractByteBufferFromImagePath(String s) {
//        try {
//            BufferedImage bi = ImageIO.read(new java.io.File(s));
//            byte[] iconData = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
//            ByteBuffer ib = createByteBuffer(iconData.length);
//            ib.order(ByteOrder.nativeOrder());
//            ib.put(iconData, 0, iconData.length);
//            ib.flip();
//            return new ByteBuffer[]{ib};
//        }
//        catch (Exception e){
//            System.out.println("Couldn't open icon image..." + e.toString());
//            return null;
//        }
//    }

}