package io.github.openground.base.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class SerializeUtil {

	public static byte[] serialize(Object object) {
	    ObjectOutputStream oos = null;
	    ByteArrayOutputStream bos = null;
	    try {
	        bos = new ByteArrayOutputStream();
	        oos = new ObjectOutputStream(bos);
	        oos.writeObject(object);
	        byte[] b = bos.toByteArray();
	        return b;
	    } catch (IOException e) {
	        System.out.println("序列化失败 Exception:" + e.toString());
	        return null;
	    } finally {
	        try {
	            if (oos != null) {
	                oos.close();
	            }
	            if (bos != null) {
	                bos.close();
	            }
	        } catch (IOException ex) {
	            System.out.println("io could not close:" + ex.toString());
	        }
	    }
	}
}
