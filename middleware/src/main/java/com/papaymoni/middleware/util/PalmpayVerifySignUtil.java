//package com.papaymoni.middleware.util;
//
//import java.util.Arrays;
//import java.util.Map;
//import java.util.Set;
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.stereotype.Component;
//
//import java.net.URLDecoder;
//@Component
//public class PalmpayVerifySignUtil {
//
//    private ObjectMapper objectMapper = new ObjectMapper();
//
//
//    public PalmpayVerifySignUtil() {
//    }
//
//   /* public static void main(String[] args) throws Exception {
//        PalmpayVerifySign palmpayVerifySign = new PalmpayVerifySign();
//        palmpayVerifySign.testCallbackVerify();
//    } */
//
//    public Boolean testCallbackVerify() throws Exception {
//        String paramBody = "{\"orderId\":\"P23101802543812274\",\"orderNo\":\"2424231018025438544222\",\"appId\":\"L10378191362\",\"transType\":\"24\",\"orderType\":\"01\",\"amount\":999,\"couponAmount\":0,\"status\":1,\"completeTime\":1697597698516,\"orderStatus\":2,\"sign\":\"Dj9ycTD91W9ti4n%2FK6GbtVu2qNDE83tB6C7ToKrVe9tSM2aIhMrk%2Fqy9CgQ9fwXJhH6QQ1kg8kx2AV7O4tOIpSXs%2BnZ7aHXjricn8pUx06yQ%2BIqBXWSY9Hrhb6qkPXAzcskQ1MI%2B7SOieBVJTMf4vxIoxLOhMSYHeQB5jyfgZBA%3D\"}";
//        String paramBody2 = "{\n" +
//                "  \"orderId\": \"d5901857as5ae-aa88-ec8a3e36b70d\",\n" +
//                "  \"orderNo\": \"2424240625105128204155\",\n" +
//                "  \"appId\": \"L70760282852\",\n" +
//                "  \"transType\": \"24\",\n" +
//                "  \"orderType\": \"01\",\n" +
//                "  \"amount\": 200000,\n" +
//                "  \"couponAmount\": 0,\n" +
//                "  \"status\": 1,\n" +
//                "  \"completeTime\": 1719312756221,\n" +
//                "  \"payerMobileNo\": \"023***0727\",\n" +
//                "  \"orderStatus\": 2,\n" +
//                "  \"sign\": \"Xq35cw8gX%2FP7FXmjWa0mR33lTHHIG6dHYD2oMzpiICyxg5LqT7w7dN3BNnpFEwfy0wl%2FxS57ELUl0RbgwdnCwMEXHav276nCZj95dJtBWjJMbfZadFLaINlBMdxETqMevhu4QVv9iVG0TFSn18F5qiqxV5MeV6HUXFWAgDRFdrc%3D\"\n" +
//                "}";
//
//
//
//        String paramBody3 = "{\"createdTime\":1722946481276,\"currency\":\"NGN\",\"orderAmount\":740000,\"orderNo\":\"MI1820795628658294784\",\"orderStatus\":1,\"payerAccountName\":\"ALI AMADU\",\"payerAccountNo\":\"**8803\",\"payerBankName\":\"ETRANZACT\",\"reference\":\"ALI AMADU TO \",\"updateTime\":1722946481276,\"virtualAccountName\":\"ALI . AMADU(TRUST CEN. LTD)\",\"virtualAccountNo\":\"6692939243\",\"appId\":\"L240719115844162218261\",\"sign\":\"j9eCvjXfHea%2FNc8CFXpj5%2BndiTLefNAGdNXPu6uanZoRw6Zv5wSXjR1Tq8Du7yPt4XTVnD7THkX7%2Bo2TEKhdj7kqjEZEBrurPPtS8V8mnmB20Z%2FuxW8EtynZQOmqslvqkGfxk3OR88gmWRn9A%2BTpHykgG9%2F%2BjJEsATMUTm%2Bd3ts%3D\"}";
//        String paramBody4 = "{\"orderId\":\"PSB20240814175102919095311\",\"orderNo\":\"2424240814165103439605\",\"appId\":\"L39255713352\",\"transType\":\"24\",\"orderType\":\"01\",\"amount\":10000,\"couponAmount\":0,\"status\":1,\"completeTime\":1723654303833,\"orderStatus\":2,\"sign\":\"PXJN8C9LSEzsbohTQVr2zVHOnLtfEJStUV8mLwTVJUB2EPZdB8yj2akm%2Bv5wM1YP1ed%2BsaY56pRcgsGoKXmHRSXonlCpQWJ%2BmVVWPlFk9%2Br%2F0inqvg%2BOs0xzg38ffTKwILil1D3X9ohzqcr1IIpQyfs5B1cOZ%2Bp4Op6IdfD8D0Q%3D\"}";
//       /* String paramBody5 = "{\n" +
//                "  \"orderId\": \"FC7168019084094038301764959\",\n" +
//                "  \"orderNo\": \"41240906140452969580\",\n" +
//                "  \"appId\": \"L39255713352\",\n" +
//                "  \"transType\": \"41\",\n" +
//                "  \"amount\": 500,\n" +
//                "  \"status\": 1,\n" +
//                "  \"completeTime\": 1725631497543,\n" +
//                "  \"errorMsg\": \"Approved or completed successfully\",\n" +
//                "  \"orderStatus\": 2,\n" +
//                "  \"sessionId\": \"999143240204096710005611318902\",\n" +
//                "  \"sign\": \"iCXeE1O%2FqPMSvCVeLRB%2FXi5b4QK0p2sGVGx5xfD3yaGNgD%2BXzRulB%2B7iMctQkZOqDhY548CmHKK2%2BlF4UbbTK4BYZQut9GoOCPa2Iyz5d%2BWo1zyybNRN3lm%2BPjniMqA3k%2FA%2FDYUe9NIT9%2BQAAWxJSK4BuYh2DzXR7Zo8x4b2lOM%3D\"\n" +
//                "}"; */
//
//        String param6 = "{\n" +
//                "\t\"orderId\": \"202502251305328178_43eadbcd-812c-4ee5-a226-c2e4385170fb-234\",\n" +
//                "\t\"orderNo\": \"2424250225050535135165\",\n" +
//                "\t\"appId\": \"L39255713352\",\n" +
//                "\t\"transType\": \"24\",\n" +
//                "\t\"orderType\": \"01\",\n" +
//                "\t\"amount\": 10000,\n" +
//                "\t\"couponAmount\": 0,\n" +
//                "\t\"status\": 1,\n" +
//                "\t\"completeTime\": 1740459968947,\n" +
//                "\t\"payerMobileNo\": \"023***0720\",\n" +
//                "\t\"orderStatus\": 2,\n" +
//                "\t\"payer\": \"{\\\"accountNo\\\":\\\"023408108080720\\\",\\\"accountName\\\":\\\"Ajibade Oluwasegun\\\",\\\"bankCode\\\":\\\"100033\\\",\\\"bankName\\\":\\\"PalmPay\\\"}\",\n" +
//                "\t\"sign\": \"aMEKEnPgmVjIP9qMdYYKNqRaFgBe6hCT9xP0k2dhB8qhbtpv2xYthMm5p5t5XPUu6e6zoHwyvwQ3jLeFhBSYWyF67tv4AtwqLhgpegl6rdwT8cY8HXHeOO%2Bz1az9SmCfpdsAssTCLcFrsbV4DIFsmkWFoFf4cZ2HERgsfydutSQ%3D\"\n" +
//                "}";
//
//        String param7 = "{\n" +
//                "  \"orderAmount\": 120000,\n" +
//                "  \"orderNo\": \"032l1t1d5f00\",\n" +
//                "  \"payerAccountNo\": \"023408108080726\",\n" +
//                "  \"createdTime\": 1746183273064,\n" +
//                "  \"orderStatus\": 1,\n" +
//                "  \"payerBankName\": \"PalmPay\",\n" +
//                "  \"currency\": \"NGN\",\n" +
//                "  \"updateTime\": 1746183273064,\n" +
//                "  \"virtualAccountName\": \"Palmpay(MerchantTest)\",\n" +
//                "  \"payerAccountName\": \"Ajibade Oluwasegun\",\n" +
//                "  \"virtualAccountNo\": \"6694479545\",\n" +
//                "  \"appId\": \"L240927093144197211431\",\n" +
//                "  \"sign\": \"EeqI706qnugydy7651djbG%2BnPNQulPd9JJ48aGaBHqwkYxqmD5M8n6MwX1tzUyeOvUqnMpK80%2FO57UtgCvJqCC7mREX3pZQXEp%2FYG46RkhzQ0RRjYZgvOTlSzX9B2WlAcHAmibpWL7DNw2cuuwWE1fXS62bGClEKYGzr9Uzd6z0%3D\"\n" +
//                "}\n";
//        //PalmPay public key in test env (not  the keys generate by merchant)
//        String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCVT+pLc1nkz9z803SOmF48bMFn0GYF4ng6nxj0ojUeu4KeNKkkw/nfureTtL77j9RpMjquJzzKdOZfHRvQyuAbaLoaSD1uU47npNiAL05bLYZEoZWvFOar9gNbIesea8MX0DeYncA2Tkr3kUo8K6XBrZ+TcV2Q8NEvm1T536LOGwIDAQAB";
//        String publicKey2 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoIUWqxE+nUhxkWm/zJvqFtfCYNMH6sDfTQA5CgSp1Hzqrh97cTKWpLCXglOr+BKRjqdVfPUTcvLGGg9rwNwlesBPZgL/Q7U+pdCuo394e0J+j2ArhT87muNGMb/XaC4zOPmM7NSPrjrT5kO5m/9U6xeU0scqNMFHKBBgF9ywYpyaeuCkDRM3GjTfKWulDLg6dsKSDIZzEBTBV4VV4/VjywNOab/JrVDM5CLmJPbe6rNBTkxs41QUsOt8Q3JtN4SnGaYA5Kq/AmetLnjAnhMFsgu2NigVkM9YhPrHTQgoffgxZA7NGMjQs+32rX2HqjHMQO2bhucLOi4ggqaxNj3t9QIDAQAB";
//
//        boolean res = verifySignForCallback(param7,publicKey, EaseIdSignUtil.SignType.RSA);
//        System.out.println("response: "+ res);
//        return res;
//    }
//
//    private static String sortStr(final Map<String, String> data) throws Exception{
//        Set<String> keySet = data.keySet();
//        String[] keyArray = keySet.toArray(new String[keySet.size()]);
//        Arrays.sort(keyArray);
//        StringBuilder sb = new StringBuilder();
//        for (String k : keyArray) {
//            if (data.get(k).trim().length() > 0) // 参数值为空，则不参与签名
//                sb.append(k).append("=").append(data.get(k).trim()).append("&");
//        }
//
//        String encryData = sb.substring(0, sb.length()-1);
//        System.out.println("encryData: " +encryData);
//        String md5 = EaseIdMd5Util.MD5(encryData);
//        System.out.println("md5Now: " +md5);
//        return md5;
//    }
//
//    private static boolean verifySignature(final Map<String, String> data, String publicKey, String sign, EaseIdSignUtil.SignType signType) throws Exception{
//        String encryData = sortStr(data);
//        if(EaseIdSignUtil.SignType.RSA.equals(signType)){
//            return EaseIdRsaUtil.verify(encryData, publicKey, sign);
//        }else{
//            return EaseIdRsaUtil.HMACSHA256(encryData, publicKey).equals(sign);
//        }
//    }
//
//    public  boolean verifySignForCallback(String paramBodyJson, String publicKey, EaseIdSignUtil.SignType signType) throws Exception {
//       // Map<String, String> data = JSON.parseObject(paramBodyJson, Map.class);
//        System.out.println(paramBodyJson);
//
//        Map<String, String> data = objectMapper.readValue(paramBodyJson, new TypeReference<Map<String, String>>() {});
//        Set<String> set = data.keySet();
//        if(EaseIdSignUtil.SignType.RSA.equals(signType)){
//            for (String key : set) {
//                data.put(key,String.valueOf(data.get(key)));
//            }
//        }
//        String sign = data.get("sign");
//        sign =  URLDecoder.decode(sign,"UTF-8");
//        System.out.println("sign: "+ sign);
//        data.remove("sign");
//        System.out.println("Data: "+data);
//        return verifySignature(data,publicKey,sign,signType);
//    }
//
//}

package com.papaymoni.middleware.util;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class PalmpayVerifySignUtil {

    private final ObjectMapper objectMapper;

    public PalmpayVerifySignUtil() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Verify signature for Palmpay webhook callback
     * @param paramBodyJson The raw JSON string from the webhook
     * @param publicKey Palmpay's public key
     * @param signType The signature type (RSA or HMACSHA256)
     * @return true if signature is valid
     */
    public boolean verifySignForCallback(String paramBodyJson, String publicKey, EaseIdSignUtil.SignType signType) throws Exception {
        // Parse the JSON string to a Map
        Map<String, String> data = objectMapper.readValue(paramBodyJson, new TypeReference<Map<String, String>>() {});
        Set<String> set = data.keySet();

        if(EaseIdSignUtil.SignType.RSA.equals(signType)){
            for (String key : set) {
                data.put(key, String.valueOf(data.get(key)));
            }
        }

        // Extract and decode the signature
        String sign = data.get("sign");
        log.info("sign: {}", sign);
        sign = URLDecoder.decode(sign,"UTF-8");

        // Remove signature from data to verify
        data.remove("sign");

        return verifySignature(data, publicKey, sign, signType);
    }

    /**
     * Verify signature
     * @param data Map of data to verify
     * @param publicKey Public key for verification
     * @param sign Signature to verify
     * @param signType Type of signature
     * @return true if signature is valid
     */
    private boolean verifySignature(final Map<String, String> data, String publicKey, String sign, EaseIdSignUtil.SignType signType) throws Exception {
        String encryData = sortStr(data);
        log.info("sortedParams: {}", encryData);
        log.info("Public Key: {}", publicKey);
        if(EaseIdSignUtil.SignType.RSA.equals(signType)){
            return EaseIdRsaUtil.verify(encryData, publicKey, sign);
        } else {
            return EaseIdRsaUtil.HMACSHA256(encryData, publicKey).equals(sign);
        }
    }

    /**
     * Sort parameters alphabetically and create a string for signature verification
     * @param data Map of parameters to sort
     * @return MD5 hash of the sorted parameter string
     */
    private String sortStr(final Map<String, String> data) throws Exception {
        Set<String> keySet = data.keySet();
        String[] keyArray = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(keyArray);

        StringBuilder sb = new StringBuilder();
        for (String k : keyArray) {
            if (data.get(k).trim().length() > 0) { // Skip empty values
                sb.append(k).append("=").append(data.get(k)).append("&");
            }
        }

        // Remove trailing & character
        String encryData = sb.substring(0, sb.length()-1);


        // Generate MD5 hash
        return EaseIdMd5Util.MD5(encryData);
    }
}
