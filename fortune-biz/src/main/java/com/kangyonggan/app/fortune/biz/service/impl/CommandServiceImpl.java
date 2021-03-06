package com.kangyonggan.app.fortune.biz.service.impl;

import com.github.pagehelper.PageHelper;
import com.kangyonggan.app.fortune.biz.service.CommandService;
import com.kangyonggan.app.fortune.biz.service.MerchantService;
import com.kangyonggan.app.fortune.biz.util.PropertiesUtil;
import com.kangyonggan.app.fortune.common.util.DateUtil;
import com.kangyonggan.app.fortune.common.util.FileUtil;
import com.kangyonggan.app.fortune.mapper.CommandMapper;
import com.kangyonggan.app.fortune.model.annotation.LogTime;
import com.kangyonggan.app.fortune.model.constants.AppConstants;
import com.kangyonggan.app.fortune.model.constants.FtpType;
import com.kangyonggan.app.fortune.model.constants.TranSt;
import com.kangyonggan.app.fortune.model.dto.CommandDto;
import com.kangyonggan.app.fortune.model.vo.Command;
import com.kangyonggan.app.fortune.model.vo.Merchant;
import com.kangyonggan.app.fortune.model.vo.ShiroMerchant;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kangyonggan
 * @since 5/5/17
 */
@Service
@Log4j2
public class CommandServiceImpl extends BaseService<Command> implements CommandService {

    @Autowired
    private CommandMapper commandMapper;

    @Autowired
    private MerchantService merchantService;

    @Override
    @LogTime
    public void saveCommand(Command command) {
        myMapper.insertSelective(command);
    }

    @Override
    @LogTime
    public Command findCommandBySerialNo(String serialNo) {
        Command command = new Command();
        command.setMerchSerialNo(serialNo);
        command.setIsDeleted(AppConstants.IS_DELETED_NO);

        return myMapper.selectOne(command);
    }

    @Override
    public void updateCommandsToSuccess() {
        Command command = new Command();
        command.setTranSt(TranSt.Y.name());

        List<String> list = new ArrayList();
        list.add(TranSt.E.name());
        list.add(TranSt.I.name());

        Example example = new Example(Command.class);
        example.createCriteria().andIn("tranSt", list);

        myMapper.updateByExampleSelective(command, example);
    }

    @Override
    public void updateCommandsToFailure() {
        Command command = new Command();
        command.setTranSt(TranSt.F.name());

        Example example = new Example(Command.class);
        example.createCriteria().andEqualTo("tranSt", TranSt.N.name()).andLessThan("createdTime", DateUtil.plusMinutes(-10));

        myMapper.updateByExampleSelective(command, example);
    }

    @Override
    @LogTime
    public List<Command> searchCommands(int pageNum, String startDate, String endDate, String tranSt) throws ParseException {
        Example example = new Example(Command.class);
        Example.Criteria criteria = example.createCriteria();
        ShiroMerchant shiroMerchant = merchantService.getShiroMerchant();

        criteria.andEqualTo("merchCo", shiroMerchant.getMerchCo());

        if (StringUtils.isNotEmpty(tranSt)) {
            criteria.andEqualTo("tranSt", tranSt);
        }
        if (StringUtils.isNotEmpty(startDate)) {
            criteria.andGreaterThanOrEqualTo("createdTime", DateUtil.fromDate(startDate));
        }
        if (StringUtils.isNotEmpty(endDate)) {
            criteria.andLessThanOrEqualTo("createdTime", DateUtil.fromDate(endDate));
        }
        criteria.andEqualTo("isDeleted", AppConstants.IS_DELETED_NO);
        example.setOrderByClause("id desc");

        PageHelper.startPage(pageNum, AppConstants.PAGE_SIZE);
        return myMapper.selectByExample(example);
    }

    @Override
    @LogTime
    public CommandDto findCommandById(Long id) {
        return commandMapper.selectCommandById(id);
    }

    @Override
    @LogTime
    public String genSettleFile(Merchant merchant, String ftpType) throws Exception {
        String settleDate = DateUtil.plusStrDays(-1);// ??????????????????????????????
        if (FtpType.fund.name().equals(ftpType)) {
            settleDate = DateUtil.getDate();// ?????????????????????????????????
        }

        // ??????????????????????????????
        Command command = new Command();
        command.setTranSt(TranSt.F.name());

        Example example = new Example(Command.class);
        List<String> trsnSts = new ArrayList();
        trsnSts.add(TranSt.E.name());
        trsnSts.add(TranSt.I.name());
        trsnSts.add(TranSt.N.name());
        example.createCriteria().andEqualTo("merchCo", merchant.getMerchCo()).andEqualTo("settleDate", settleDate).andIn("tranSt", trsnSts);
        myMapper.updateByExampleSelective(command, example);

        // ?????????????????????????????????
        command = new Command();
        command.setMerchCo(merchant.getMerchCo());
        command.setSettleDate(settleDate);
        command.setIsDeleted(AppConstants.IS_DELETED_NO);
        List<Command> commands = myMapper.select(command);
        log.info("??????{}???{}??????{}?????????.", merchant.getMerchNm(), settleDate, commands.size());

        // ????????????????????????|??????????????????|??????????????????|?????????|????????????(????????????)|??????????????????
        StringBuilder fileContent = new StringBuilder();
        for (Command comm : commands) {
            fileContent.append(comm.getTranCo()).append(AppConstants.FILE_SPLIT).append(comm.getMerchSerialNo())
                    .append(AppConstants.FILE_SPLIT).append(comm.getFpaySerialNo()).append(AppConstants.FILE_SPLIT).append(comm.getProtocolNo())
                    .append(AppConstants.FILE_SPLIT).append(comm.getAmount()).append(AppConstants.FILE_SPLIT).append(comm.getTranSt()).append("\r\n");
        }
        if (fileContent.length() > 0) {
            fileContent.deleteCharAt(fileContent.lastIndexOf("\r"));
            fileContent.deleteCharAt(fileContent.lastIndexOf("\n"));
        }
        String filePath = PropertiesUtil.getProperties("settle.dir") + File.separator + merchant.getMerchCo() + File.separator + merchant.getMerchCo() + "_" + ftpType + "_" + settleDate + ".txt";
        FileUtil.writeTextToFile(filePath, fileContent.toString());
        log.info("??????{}??????????????????????????????:{}", merchant.getMerchNm(), filePath);

        return filePath;
    }
}
