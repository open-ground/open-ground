package io.github.openground.land.common.util;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Sftp工具类
 *
 * @author jack.zhang
 * @version v 1.0
 * @date 2019年3月11日 上午9:58:02
 */
@Slf4j
public class SftpUtil {

    private static SftpUtil sftpUtil;

    // private ChannelSftp channelSftp;

    private Session session;

    private String username;

    private String password;

    private String host;

    private int port;

    /**
     * 获取单实例
     *
     * @return
     * @author jack.zhang
     * @data 2019年3月11日 上午9:58:24
     */
    public synchronized static SftpUtil getInstance() {
        if (sftpUtil == null) {
            sftpUtil = new SftpUtil();
        }
        return sftpUtil;
    }

    /**
     * 获取SFTP连接通道
     *
     * @param username
     * @param password
     * @param host
     * @param port
     * @return
     * @throws Exception
     * @author jack.zhang
     * @data 2019年3月11日 上午9:58:33
     */
    public synchronized ChannelSftp getChannelSftp(String username, String password, String host, int port) throws Exception {
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;

        ChannelSftp channelSftp = getchannel();
        return channelSftp;
    }

    /**
     * 获取连接通道
     *
     * @return
     * @throws Exception
     * @author jack.zhang
     * @data 2019年3月11日 上午10:02:24
     */
    private ChannelSftp getchannel() throws Exception {
        JSch jsch = new JSch();
        log.info("获取session");
        session = jsch.getSession(username, host, port);
        log.info("设置密码");
        session.setPassword(password);
        Properties properties = new Properties();
        log.info("设置配置信息");
        properties.put("StrictHostKeyChecking", "no");
        session.setConfig(properties);
        session.setTimeout(10000);
        log.info("session连接");
        session.connect();
        log.info("打开通道channel");
        Channel channel = session.openChannel("sftp");
        log.info("开始连接通道channel");
        channel.connect();
        log.info("成功获得连接通道channel");
        return (ChannelSftp) channel;
    }

    /**
     * 关闭连接
     *
     * @author jack.zhang
     * @data 2019年3月11日 上午10:02:48
     */
    public void closeChannel(ChannelSftp channelSftp) {
        if (channelSftp != null) {
            channelSftp.disconnect();
            log.info("channelSftp断开");
        }
        if (session != null) {
            session.disconnect();
            log.info("session断开");
        }
    }

    /**
     * 下载文件
     *
     * @param remotePath 远程目录
     * @param fileName   文件名称
     * @param localPath  本地下载路径
     * @return
     * @throws Exception
     * @author jack.zhang
     * @data 2019年3月11日 上午9:29:49
     */
    public boolean downloadFile(ChannelSftp channelSftp, String remotePath, String fileName, String localPath) throws Exception {
        log.info("开始下载文件: " + remotePath + fileName + "， 到本地：" + localPath);
        FileOutputStream fos = null;
        File dir = new File(localPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File localfile = new File(localPath, fileName);
        try {
            fos = new FileOutputStream(localfile);
            channelSftp.get(remotePath + fileName, fos);
        } catch (Exception e) {
            log.error("文件下载失败", e);
            return false;
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                    fos = null;
                }
            } catch (IOException e1) {
                log.error(e1.getMessage(), e1);
            }
        }
        return true;
    }

    /**
     * 判断文件是否存在
     *
     * @param remotePath
     * @param fileName
     * @return
     * @author jack.zhang
     * @data 2019年3月11日 上午9:48:33
     */
    public boolean fileIsExist(ChannelSftp channelSftp, String remotePath, String fileName) {
        log.info("检查目录:" + remotePath + "是否存在文件: " + fileName);
        try {
            if (!channelSftp.isConnected()) {
                this.closeChannel(channelSftp);
                this.getchannel();
            }
            channelSftp.ls(remotePath + fileName);
        } catch (Exception e) { // IOException
            log.info("", e);
            log.info(remotePath + fileName + "文件不存在!");
            return false;
        }
        return true;
    }
}
