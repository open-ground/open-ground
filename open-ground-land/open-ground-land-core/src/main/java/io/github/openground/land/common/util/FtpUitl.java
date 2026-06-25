package io.github.openground.land.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
public class FtpUitl {

    private static FtpUitl ftpUitl;

    public synchronized static FtpUitl getInstance() {
        if (ftpUitl == null) {
            ftpUitl = new FtpUitl();
        }
        return ftpUitl;
    }

    public synchronized FTPClient getFtpClient(String username, String password, String host, int port, String encoding) {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.setConnectTimeout(10 * 1000);
            ftpClient.connect(host, port);
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                log.error("服务器" + host + "拒绝连接");
                return null;
            }
            log.info("登陆FTP服务器");
            boolean b = ftpClient.login(username, password);
            if (!b) {
                ftpClient.disconnect();
                log.error("服务器" + host + "连接失败");
                return null;
            }
            ftpClient.setBufferSize(1024);
            ftpClient.setControlEncoding(encoding);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            // ftpClient.setDataTimeout(300000);
            ftpClient.enterLocalPassiveMode();
        } catch (Exception e) {
            log.error("连接ftp异常", e);
            ftpClient = null;
        }

        return ftpClient;
    }

    public boolean fileIsExist(FTPClient ftpClient, String remotePath, String fileName) {
        // 方法2 循环判断文件是否存在
        boolean fileExistFlag = false;
        try {
            FTPFile[] files = ftpClient.listFiles(remotePath);
            log.info("FTP目录:" + remotePath + " 中共发现" + files.length + "个文件");
            // 检查文件是否存在
            ftpClient.changeWorkingDirectory(remotePath);
            // 方法1 判断文件是否存在
            // InputStream is = ftpClient.retrieveFileStream(fileName);
            // if (is == null || ftpClient.getReplyCode() == FTPReply.FILE_UNAVAILABLE) {
            // // 文件不存在
            // log.info(fileName + "文件不存在" + task.getTaskName() + ",执行条件不满足");
            // return;
            // }
            // is.close();
            /**
             * 在每次执行完下载操作之后，completePendingCommand()会一直在等FTP Server返回226 Transfer
             * complete，但是FTP
             * Server只有在接受到InputStream 执行close方法时，才会返回。所以一定先要执行close方法。不然在第一次下载一个文件成功之后，
             * 之后再次获取inputStream 就会返回null
             */
            // ftpClient.completePendingCommand();
            /**
             * 若前面使用了retrieveFileStream，没有调用completePendingCommand，则需要主动调用一次getReply()把接下来的226消费掉.
             * 这样做是也可以解决retrieveFile返回false的问题
             */
            // ftpClient.getReply();

            for (FTPFile ftpFile : files) {
                if (fileName.equals(ftpFile.getName())) {
                    fileExistFlag = true;
                    break;
                }
            }
            return fileExistFlag;
        } catch (Exception e) {
            log.error("ftp异常", e);
        }
        return fileExistFlag;

    }

    public boolean downloadFile(FTPClient ftpClient, String remotePath, String fileName, String localPath) {
        FileOutputStream fos = null;
        try {
            // 下载文件到本地目录
            File dir = new File(localPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File localfile = new File(localPath, fileName);
            fos = new FileOutputStream(localfile);
            boolean flag = ftpClient.retrieveFile(fileName, fos);
            if (!flag) {
                log.error("文件下载失败");
                return false;
            }
            log.info("成功下载文件：" + fileName);
        } catch (Exception e) {
            log.error("下载异常", e);
            return false;
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                    fos = null;
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return true;
    }

    public void closeftp(FTPClient ftpClient) {
        try {
            if (ftpClient.isConnected()) {
                log.info("关闭链接");
                ftpClient.logout();
                ftpClient.disconnect();
            }
        } catch (Exception e) {
            log.error("系统异常！", e);
        }
    }

}
