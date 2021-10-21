
import com.alibaba.fastjson.JSONObject;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import io.swagger.annotations.Api;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

/**
 * @program:
 * @description:
 * @author: tmh
 * @create: 2020-11-27 15:30
 **/
@Api(tags = "支付接口")
@RestController
@RequestMapping("/api/pay")
public class PayController {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostMapping("/iosPay")
    public BaseResponse iosPay(@RequestBody IosPayDto iosPayDto) {
        String transactionId=iosPayDto.getTransactionId(); //交易id
        String payload=iosPayDto.getPayload();

//        logger.info("苹果内购校验开始，交易ID：" + transactionId + " base64校验体：" + payload);
        //获取用户id
        Subject subject = SecurityUtils.getSubject();
        Member member = (Member) subject.getPrincipals().getPrimaryPrincipal();
        VipOrder model= new  VipOrder ();
        if (member != null && StringUtils.isNotEmpty(member.getUid())) {

            model.setUid(member.getUid());
        }


        //开始回验
        String verifyResult = IosVerifyUtil.buyAppVerify(payload, 1);
        if (verifyResult == null) {
            return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"苹果验证失败，返回数据为空");
        } else {
            logger.info("线上，苹果平台返回JSON:" + verifyResult);
            JSONObject appleReturn = JSONObject.parseObject(verifyResult);
            String states = appleReturn.getString("status");
            //无数据则沙箱环境验证
            if ("21007".equals(states)) {
                verifyResult = IosVerifyUtil.buyAppVerify(payload, 0);
                logger.info("沙盒环境，苹果平台返回JSON:" + verifyResult);
                appleReturn = JSONObject.parseObject(verifyResult);
                states = appleReturn.getString("status");
            }
            logger.info("苹果平台返回值：appleReturn" + appleReturn);
            // 前端所提供的收据是有效的    验证成功
            if (states.equals("0")) {
                String receipt = appleReturn.getString("receipt");
                JSONObject returnJson = JSONObject.parseObject(receipt);
                String inApp = returnJson.getString("in_app");
                List<HashMap> inApps = JSONObject.parseArray(inApp, HashMap.class);
                if (!CollectionUtils.isEmpty(inApps)) {
                    ArrayList<String> transactionIds = new ArrayList<String>();

                    boolean flag=false;
                    for (HashMap app : inApps) {
                        String  atransaction_id=app.get("transaction_id").toString();
                        String aproduct_id=app.get("product_id").toString();

                        if (StringUtils.isNotBlank(aproduct_id) && StringUtils.isNotBlank(atransaction_id)) {
                            //判重，避免重复分发内购商品。收到客户端上报的transaction_id后，直接MD5后去数据库查，能查到说明是重复订单就不做处理
                            if(transactionId.equals(atransaction_id)) {
                                //添加业务逻辑 vip或者道具
                                if (!vipOrdrService.isExist("ios", transactionId)) {

                                    }
                                    else
                                        return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR, "数据库保存订单失败");


                                } else
                                    return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR, "请勿重复消费");
                            }
                        }
                    }
                    if(!flag)
                        return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"当前交易不在交易列表中");
                    else{
                        //永远不会进来
                        return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR, "数据库保存订单失败");
                    }




                }
                else
                    return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"未能获取获取到交易列表" + states);

            } else {
                return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"支付失败，错误码：" + states);

            }
        }

    }
    @PostMapping("/androidPay")
    public BaseResponse androidPay(@RequestBody GooglePayDto googlePayDto)
    {
        String productId=googlePayDto.getProductId();
        String orderId=googlePayDto.getOrderId();
        String purchaseToken=googlePayDto.getPurchaseToken();
        String email="umexxxx@pc-api-5677xxxxxx-94.iam.gserviceaccount.com";
        try {
            Subject subject = SecurityUtils.getSubject();
            Member member = (Member) subject.getPrincipals().getPrimaryPrincipal();
            VipOrder model= new  VipOrder ();
            if (member != null && StringUtils.isNotEmpty(member.getUid())) {

                model.setUid(member.getUid());
            }
            //创建google身份
            GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(googleConfig.getJsonpath()))
                    .createScoped(AndroidPublisherScopes.all());//createScoped给令牌访问权限设置使用的权限范围
            //credential.refreshToken();//注意这里
            if(credential==null) {
                logger.error("Get GoogleCredential fails获取谷歌凭证失败！");
            }
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
            AndroidPublisher publisher = new AndroidPublisher.Builder(httpTransport, JSON_FACTORY, credential).build();

            AndroidPublisher.Purchases.Products products = publisher.purchases().products();
            AndroidPublisher.Purchases.Products.Get product = products.get("com.xxx.xxxx", productId,purchaseToken);//引号内包名
            ProductPurchase purchase= product.execute();
            if(purchase!=null) {
                if (purchase.getPurchaseState() == 0)//0. Purchased 1. Canceled 2. Pending
                {
                    if (purchase.getConsumptionState() == 1)//0. Yet to be consumed 1. Consumed
                    {
                        if(purchase.getPurchaseType().equals(0))
                        {

                            logger.info("沙盒环境谷歌返回JSON:"+purchase.toString());
                        }

                        //添加处理业务逻辑

                        //
                    }
                    else
                    {
                        return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"订单未支付");
                    }
                }
                else
                {
                    return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"订单未支付");
                }
            }
            else
                return ReturnResponseUtil.Error(ReturnResponseUtil.BUSSINESS_ERROR,"支付失败purchase为空");
            // 通过consumptionState, purchaseState可以判断订单的状态

        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.info(ex.getMessage());
        }
        return null;
    }
}
