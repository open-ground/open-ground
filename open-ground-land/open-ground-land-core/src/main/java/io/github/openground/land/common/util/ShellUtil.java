package io.github.openground.land.common.util;

import ch.ethz.ssh2.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * shell 工具类
 *
 * @author jack.zhang
 * @version v 1.0
 * @date 2019年3月17日 下午12:52:08
 */
@Slf4j
public class ShellUtil {

    private static ShellUtil shellUtil;

//	private Connection connection;

    /**
     * 远程 Shell 环境的字符集
     */
    private String charset = Charset.defaultCharset().toString();


    /**
     * 获取单实例
     *
     * @return
     * @author jack.zhang
     * @data 2019年3月11日 上午9:58:24
     */
    public synchronized static ShellUtil getInstance() {
        if (shellUtil == null) {
            shellUtil = new ShellUtil();
        }
        return shellUtil;
    }

    /**
     * 获得连接
     *
     * @param username
     * @param password
     * @param host
     * @param port
     * @return
     * @author jack.zhang
     * @data 2019年3月17日 下午12:52:36
     */
    public Connection getConnection(String username, String password, String host, int port) {
//		if (connection != null && connection.isAuthenticationComplete()) {
//			log.info("获得当前活动连接");
//			return true;
//		}
        Connection connection = new Connection(host, port);
        try {
            connection.connect();
            log.info("获得新的连接");
            connection.authenticateWithPassword(username, password);
        } catch (IOException e) {
            log.error("login error", e);
            return null;
        }
        return connection;
    }

    /**
     * 关闭连接
     *
     * @author jack.zhang
     * @data 2019年3月11日 上午10:02:48
     */
    public void closeConnection(Connection connection) {
        if (connection != null) {
            connection.close();
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
    public boolean downloadFile(Connection connection, String remotePath, String fileName, String localPath) throws Exception {
        log.info("开始下载文件: " + remotePath + fileName + "， 到本地：" + localPath);
        FileOutputStream fos = null;
        File dir = new File(localPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File localfile = new File(localPath, fileName);
        try {
            fos = new FileOutputStream(localfile);
            SCPClient scp = new SCPClient(connection);
            scp.get(remotePath + fileName);
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
    @SuppressWarnings("unchecked")
    public boolean fileIsExist(Connection connection, String remotePath, String fileName) {
        log.info("检查目录:" + remotePath + "是否存在文件: " + fileName);
        List<String> fileLists = new ArrayList<String>();
        try {
            if (connection == null) {
                log.error("connection is null");
                return false;
            }
            SFTPv3Client s3c = new SFTPv3Client(connection);
            Vector<SFTPv3DirectoryEntry> fileSets = null;
            try {
                fileSets = (Vector<SFTPv3DirectoryEntry>) s3c.ls(remotePath);
            } catch (SFTPException e) {
                log.warn("No such file");
            } finally {
                s3c.close();
            }
            if (fileSets == null || fileSets.size() <= 0) {
                log.info("fileSets is null or fileSets size <= 0");
                return false;
            }
            for (SFTPv3DirectoryEntry s3de : fileSets) {
                SFTPv3FileAttributes s3fa = s3de.attributes;
                if (s3de.filename.startsWith("."))
                    continue;
                if (s3fa.isRegularFile()) {
                    fileLists.add(s3de.filename);
                }
            }
        } catch (Exception e) {
            log.error("查看远程文件异常", e);
        }
        log.info("FTP目录:" + remotePath + " 中共发现" + fileLists.size() + "个文件");
        return fileLists.contains(fileName);
    }

    /**
     * 执行Shell命令
     *
     * @param cmds
     * @return
     * @author jack.zhang
     * @data 2019年3月17日 下午12:45:19
     */
    public String exec(Connection connection, String cmds) {
        InputStream in = null;
        String result = "";
        Session session = null;
        try {
            session = connection.openSession();
            session.execCommand(cmds);

            in = session.getStdout();
            result = processStdout(in, this.charset);
        } catch (IOException e) {
            log.error("exec error", e);
        } finally {
            if (null != session) {
                session.close();
            }
            if (null != connection) {
                connection.close();
            }
        }
        return result;
    }

    /**
     * 获取Shell的执行结果
     *
     * @param in
     * @param charset
     * @return
     * @author jack.zhang
     * @data 2019年3月17日 下午12:45:33
     */
    private String processStdout(InputStream in, String charset) {
        InputStreamReader isr = null;
        BufferedReader br = null;
        StringBuffer sb = new StringBuffer();
        String tmpStr = null;
        try {
            isr = new InputStreamReader(in, charset);
            br = new BufferedReader(isr);
            if (null != br) {
                while ((tmpStr = br.readLine()) != null) {
                    sb.append(tmpStr);
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
            log.error("processStdout error", e);
        } finally {
            if (null != br) {
                try {
                    br.close();
                } catch (IOException e) {
                    log.error("no process", e);
                }
            }
            if (null != isr) {
                try {
                    isr.close();
                } catch (IOException e) {
                    log.error("no process", e);
                }
            }
        }

        return sb.toString();
    }
}
