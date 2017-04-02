asdasdasdasdasdasdasdasdasdasdasdasdasdasdasd

package org.ptst.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class CryptoUtils {
	private static final String KEY_FILE = "keys.bin";
	private static final String PK_ALG = "RSA"; // if you have 5 minutes to generate a key, change to DH
	private static final int PK_SIZE = 2048;
	private static final String KEY = "PBEWITHSHA256AND256BITAES-CBC-BC";
	private static final String TRANSFORM = "PBEWITHSHA256AND256BITAES-CBC-BC";
	private static final String DIGEST = "SHA-1";
	private static final String PROVIDER = "BC";
	
	private static PBEParameterSpec paramSpec;
	private static BASE64Encoder encoder;
	private static BASE64Decoder decoder;
	private static MessageDigest digest;
	private static SSLSocketFactory sslSocketFactory;
	private static Cipher cipher;
	
	private static PrivateKey keyPriv;
	private static PublicKey keyPub;
	private static SecretKey key;
	
	private static SecureRandom random;
	
	private static final int count = 20;
	
	private static byte[] salt;
	
	public static void init(char[] password) 
	throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
	NoSuchProviderException, FileNotFoundException, IOException, ClassNotFoundException,
	InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		
		System.out.println("Initializing crypto subsystem");
		
		InputStream in = new FileInputStream(getKeyFile());
		ObjectInputStream obi = new ObjectInputStream(in);
		
		byte[] encKey, decKey = null;
		
		System.out.println("Loading keys");
		
		try {
			
			keyPub = (PublicKey)obi.readObject();
			salt = (byte[])obi.readObject();
			encKey = (byte[])obi.readObject();
			
		} finally {
			obi.close();
			in.close();
		}
		
		System.out.println("Setting up symmetric AES cipher");
		
		paramSpec = new PBEParameterSpec(salt, count);
		PBEKeySpec keySpec = new PBEKeySpec(password);
		
		SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY, PROVIDER);
		key = factory.generateSecret(keySpec);
		
		cipher = Cipher.getInstance(TRANSFORM, PROVIDER);
		
		try {
			
			System.out.println("Decrypting private key");
		
			cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
			decKey = cipher.doFinal(encKey);
			
			in = new ByteArrayInputStream(decKey);
			obi = new ObjectInputStream(in);
		
			keyPriv = (PrivateKey)obi.readObject();
			
		} finally {
			
			System.out.println("Cleaning up");
			
			obi.close();
			in.close();
			
			for(int i=0; i<decKey.length; i++) {
				decKey[i] = 0;
			}
			
			obi = null;
			in = null;
			decKey = null;
			
			System.gc();
		}
		
		cipher = Cipher.getInstance(PK_ALG, PROVIDER);
	}
	
	public static void disableDecrypt() {
		
		System.out.println("Disabling private key decryption");
		
		keyPriv = null;
		key = null;
		paramSpec = null;
		
		System.gc();
	}
	
	static {
		random = new SecureRandom();
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		try {
			digest = MessageDigest.getInstance(DIGEST, PROVIDER);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String base64Encode(byte[] data) {
		if(encoder == null) {
			encoder = new BASE64Encoder();
		}
		return encoder.encode(data);
	}
	
	public static byte[] base64Decode(String data) throws IOException {
		if(decoder == null) {
			decoder = new BASE64Decoder();
		}
		return decoder.decodeBuffer(data);
	}
	
	public static String hexString(byte[] data) {
		if(data == null) { return null; }
		StringBuffer result = new StringBuffer();
		
		for(byte b : data) {
			result.append(String.format("%02x", b & 0xFF));
		}
		
		return result.toString();
	}
	
	public static byte[] digest(byte[] data) {
		synchronized(digest) {
			return digest.digest(data);
		}
	}
	
	public static String hexDigest(byte[] data) {
		return hexString(digest(data));
	}
	
	public static byte[] encrypt(byte[] data) 
	throws InvalidAlgorithmParameterException, InvalidKeyException, 
	BadPaddingException, IllegalBlockSizeException, IOException {
		return crypt(data, Cipher.PUBLIC_KEY, keyPub);
	}
	
	public static byte[] decrypt(byte[] data) 
	throws InvalidAlgorithmParameterException, InvalidKeyException,
	BadPaddingException, IllegalBlockSizeException, IOException {
		return crypt(data, Cipher.PRIVATE_KEY, keyPriv);	
	}
	
	private static final byte[] crypt(byte[] data, int mode, Key key) 
	throws InvalidAlgorithmParameterException, InvalidKeyException, 
	BadPaddingException, IllegalBlockSizeException, IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		synchronized(cipher) {
			cipher.init(mode, key);
			int bLen = cipher.getBlockSize();
			int dLen = data.length;
			int r = 0;
			for(int i=0; i<dLen; i+=bLen) {
				if(dLen - i <= bLen) {
					r = dLen - i;
				} else {
					r = bLen;
				}
				result.write(cipher.doFinal(data, i, r));
			}
		}
		return result.toByteArray();
	}
	
	public static byte[] encryptObject(Object obj) throws InvalidAlgorithmParameterException, InvalidKeyException, 
	BadPaddingException, IllegalBlockSizeException, IOException {
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream obo = new ObjectOutputStream(out);
		obo.writeObject(obj);
		
		obo.flush();
		out.flush();
		
		return encrypt(out.toByteArray());
		
	}
	
	public static Object decryptObject(byte[] encrypted)
	throws InvalidAlgorithmParameterException, InvalidKeyException,
	BadPaddingException, IllegalBlockSizeException, IOException, ClassNotFoundException {
		
		byte[] decrypted = decrypt(encrypted);
		
		ByteArrayInputStream in = new ByteArrayInputStream(decrypted);
		ObjectInputStream obi = new ObjectInputStream(in);
		
		return obi.readObject();
		
	}
	
	public static SSLSocketFactory getSocketFactory() 
	throws KeyManagementException, NoSuchAlgorithmException {
		if(sslSocketFactory == null) {
			
			final X509TrustManager mgr = new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() { return null; }
				public void checkServerTrusted(X509Certificate[] certificates, String s) {}
				public void checkClientTrusted(X509Certificate[] certificates, String s) {}
			};
			
			final SSLContext context = SSLContext.getInstance("ssl");
			context.init(
					null,
					new TrustManager[] { mgr },
					random
			);
			
			sslSocketFactory = context.getSocketFactory();
		}
		
		return sslSocketFactory;
	}
	
	private static final String getKeyFile() {
		return Configuration.getUserDir() + File.separator + KEY_FILE;
	}
	
	public static final void firstTimeGenerateCredentials(char[] password) 
	throws NoSuchAlgorithmException, NoSuchProviderException, 
	FileNotFoundException, IOException, NoSuchPaddingException,
	InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException,
	BadPaddingException, InvalidAlgorithmParameterException {
		
		KeyPairGenerator kpg = KeyPairGenerator.getInstance(PK_ALG, PROVIDER);
		kpg.initialize(PK_SIZE, random);
		KeyPair pair = kpg.generateKeyPair();
		
		PrivateKey key = pair.getPrivate();
		PublicKey keyPub = pair.getPublic();
		
		OutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream obo = new ObjectOutputStream(out);
		
		obo.writeObject(key);
		obo.flush();
		
		byte[] salt = new byte[8];
		random.nextBytes(salt);
		
		paramSpec = new PBEParameterSpec(salt, count);
		PBEKeySpec keySpec = new PBEKeySpec(password);
		
		SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY, PROVIDER);
		SecretKey secretKey = factory.generateSecret(keySpec);
		
		Cipher cipher = Cipher.getInstance(TRANSFORM, PROVIDER);
		cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec);
		byte[] encKey = cipher.doFinal(((ByteArrayOutputStream)out).toByteArray());
		
		String keyFile = getKeyFile();
		System.out.println("Writing keys to " + keyFile);
		
		out = new FileOutputStream(keyFile);
		obo = new ObjectOutputStream(out);
		try {
			
			obo.writeObject(keyPub);
			obo.writeObject(salt);
			obo.writeObject(encKey);
			
			obo.flush();
			out.flush();
			
		} finally {
			
			obo.close();
			out.close();
			
			key = null;
			keySpec = null;
			paramSpec = null;
			secretKey = null;
			cipher = null;
			
			System.gc();
			
		}
	}
	
	public static void main(String[] args) throws Exception {
		
		char[] password = "test".toCharArray();
		firstTimeGenerateCredentials(password);
		
//		init(password);
//		
//		byte[] enc = encrypt("There once was a man from nantucket".getBytes());
//		System.out.println(base64Encode(enc));
//		
//		byte[] plain = decrypt(enc);
//		System.out.println(new String(plain));
//		
//		disableDecrypt();
	}
}
