import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

String APK="/workspace/project/ytmusic-apk/YouTubeMusic_9.44.52_APKPure.apk";
String hex(byte[] b){ StringBuilder s=new StringBuilder(); for(byte x:b) s.append(String.format("%02x",x)); return s.toString(); }

ApkVerifier.Result res = new ApkVerifier.Builder(new File(APK)).build().verify();
System.out.println("verified overall   : " + res.isVerified());
System.out.println("verified v1 (JAR)  : " + res.isVerifiedUsingV1Scheme());
System.out.println("verified v2        : " + res.isVerifiedUsingV2Scheme());
System.out.println("verified v3        : " + res.isVerifiedUsingV3Scheme());
System.out.println("errors             : " + res.getErrors());
System.out.println("warnings           : " + res.getWarnings());

var certs = res.getSignerCertificates();
System.out.println("signer certs       : " + certs.size());
for (X509Certificate c : certs) {
    System.out.println("  subject : " + c.getSubjectX500Principal());
    System.out.println("  issuer  : " + c.getIssuerX500Principal());
    System.out.println("  sha256  : " + hex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())));
    System.out.println("  dates   : " + c.getNotBefore() + " .. " + c.getNotAfter());
    c.checkValidity();
    System.out.println("  validity: VALID");
}
System.out.println("DONE");
/exit
