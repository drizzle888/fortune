package com.kangyonggan.app.fortune.biz.service.impl;

import com.kangyonggan.app.fortune.biz.service.CommandService;
import com.kangyonggan.app.fortune.biz.service.FpayHelper;
import com.kangyonggan.app.fortune.biz.service.FpayService;
import com.kangyonggan.app.fortune.biz.service.ProtocolService;
import com.kangyonggan.app.fortune.common.exception.BuildException;
import com.kangyonggan.app.fortune.common.util.DateUtil;
import com.kangyonggan.app.fortune.common.util.XStreamUtil;
import com.kangyonggan.app.fortune.model.constants.AppConstants;
import com.kangyonggan.app.fortune.model.constants.RespCo;
import com.kangyonggan.app.fortune.model.constants.TranCo;
import com.kangyonggan.app.fortune.model.constants.TranSt;
import com.kangyonggan.app.fortune.model.vo.Protocol;
import com.kangyonggan.app.fortune.model.xml.Body;
import com.kangyonggan.app.fortune.model.xml.Fpay;
import com.kangyonggan.app.fortune.model.xml.Header;
import com.thoughtworks.xstream.XStream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author kangyonggan
 * @since 5/4/17
 */
@Service
@Log4j2
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class FpayServiceImpl implements FpayService {

    @Autowired
    private ProtocolService protocolService;

    @Autowired
    private CommandService commandService;

    @Autowired
    private FpayHelper fpayHelper;

    @Override
    public void sign(Fpay fpay) throws BuildException, Exception {
        log.info("==================== 进入发财付平台签约入口 ====================");
        Header header = fpay.getHeader();
        String merchCo = header.getMerchCo();

        // 判断是否已经有签约记录
        Body body = fpay.getBody();
        Protocol protocol = protocolService.findProtocolByMerchCoAndAcctNo(merchCo, body.getAcctNo());
        String protocolNo;
        if (protocol == null) {
            // 第一次签约
            protocolNo = fpayHelper.genProtocolNo();
            Protocol prot = new Protocol();
            prot.setMerchCo(header.getMerchCo());
            prot.setProtocolNo(protocolNo);
            prot.setAcctNo(body.getAcctNo());
            prot.setAcctNm(body.getAcctNm());
            prot.setIdNo(body.getIdNo());
            prot.setIdTp(body.getIdTp());
            prot.setMobile(body.getMobile());

            protocolService.saveProtocol(prot);
            log.info("新协议保存成功");
        } else {
            // 重复签约
            protocolNo = protocol.getProtocolNo();
            Protocol prot = new Protocol();
            prot.setId(protocol.getId());
            prot.setIsUnsign((byte) 0);
            protocolService.updateProtocol(prot);
            log.info("重复签约，已激活协议号");
        }

        RespCo resp = RespCo.RESP_CO_0000;
        // 更新交易状态, 交易金额后两位即响应码，没对应的响应码则为成功, 签约解约月查询写死成功。
        commandService.updateComanndTranSt(header.getSerialNo(), resp.getTranSt());
        log.info("更新交易状态成功");

        // 组装响应报文
        header.setRespCo(resp.getRespCo());
        header.setRespMsg(resp.getRespMsg());
        body.setProtocolNo(protocolNo);
        log.info("==================== 离开发财付平台签约入口 ====================");
    }

    @Override
    public void unsign(Fpay fpay) throws BuildException, Exception {
    }

    @Override
    public void pay(Fpay fpay) throws BuildException, Exception {
    }

    @Override
    public void redeem(Fpay fpay) throws BuildException, Exception {
    }

    @Override
    public void query(Fpay fpay) throws BuildException, Exception {
    }

    @Override
    public void queryBalance(Fpay fpay) throws BuildException, Exception {
    }
}