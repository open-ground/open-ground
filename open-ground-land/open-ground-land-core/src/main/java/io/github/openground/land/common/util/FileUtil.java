package io.github.openground.land.common.util;

import com.google.common.collect.Lists;
import io.github.openground.land.common.exception.SystemException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>Title: FileUtil</p>
 * <p>Description: 文件工具类</P>
 *
 * @Author:jack.zhang
 * @Date 2021/8/3 17:19
 * @Version 3.0.0
 */
@Slf4j
public class FileUtil {


    /**
     * <p>Description: 写入文件内容，文件不存在自动创建</P>
     *
     * @param fileFullName 文件全路径及名称
     * @param content      要写入的内容
     * @return void
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2021/8/3 17:21
     */
    public static void writeFile(String fileFullName, String content) {
        FileWriter writer = null;
        try {
            File file = new File(fileFullName);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            boolean b = true;
            if (!file.exists()) {
                b = file.createNewFile();
            }
            if (b) {
                writer = new FileWriter(file, true);
                writer.write(content + "\n");
            }
        } catch (Exception e) {
            log.error(fileFullName + "文件写入异常", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.debug("流关闭异常", e);
                }
            }
        }
    }

    /**
     * <p>Description: 按指定大小切分文件</P>
     *
     * @param fileFullName 需要被拆分的文件路径
     * @param fileSize     文件大小
     * @return void
     * @Author:jack.zhang
     * @Date 2022/9/7 13:33
     */
    public static void splitFileBySize(String fileFullName, int fileSize) {

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File(fileFullName)))) {
            for (int i = 0; ; i++) {
                String filePath = fileFullName + "_" + i;
                if (singleSplitFile(bis, filePath, fileSize)) break;
            }
        } catch (IOException e) {
            log.error("文件切分异常", e);
        }
    }

    /**
     * @param bis           输入流
     * @param filePath      新文件存储的文件夹路径
     * @param partitionSize 每个文件的大小 单位MB
     * @throws IOException
     */
    private static boolean singleSplitFile(BufferedInputStream bis, String filePath, int partitionSize) throws IOException {
        int length = partitionSize * 1024 * 1024;
        byte[] buffer = new byte[1024];
        int tempLength;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(filePath)))) {
            while ((tempLength = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, tempLength);
                if ((length = length - tempLength) <= 0) break;
            }
        }
        return tempLength < 0;
    }


    /**
     * @param filePath  文件路径
     * @param fileName  文件名（如：abc.txt）
     * @param fileCount 分割文件数量
     * @throws IOException
     */
    public static List<String> splitFileByCount(String filePath, String fileName, int fileCount) throws Exception {
        Date start = new Date();
        log.info("开始切割文件,文件数量:{}", fileCount);
        List<String> fileList = new ArrayList<>();
        if (fileCount == 1) {
            fileList.add(fileName);
            return fileList;
        }
        FileInputStream fis = new FileInputStream(filePath + fileName);
        FileChannel inputChannel = fis.getChannel();
        final long fileSize = inputChannel.size();
        if (fileSize <= 0) {
            return Lists.newArrayList();
        }
        long average = fileSize / fileCount;//平均值
        long bufferSize = 200; //缓存块大小，自行调整
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.valueOf(bufferSize + "")); // 申请一个缓存区
        long startPosition = 0; //子文件开始位置
        long endPosition = average < bufferSize ? 0 : average - bufferSize;//子文件结束位置
        for (int i = 0; i < fileCount; i++) {
            if (i + 1 != fileCount) {
                int read = inputChannel.read(byteBuffer, endPosition);// 读取数据
                readW:
                while (read != -1) {
                    byteBuffer.flip();//切换读模式
                    byte[] array = byteBuffer.array();
                    for (int j = 0; j < array.length; j++) {
                        byte b = array[j];
                        if (b == 10 || b == 13) { //判断\n\r
                            endPosition += j;
                            break readW;
                        }
                    }
                    endPosition += bufferSize;
                    byteBuffer.clear(); //重置缓存块指针
                    read = inputChannel.read(byteBuffer, endPosition);
                }
            } else {
                endPosition = fileSize; //最后一个文件直接指向文件末尾
            }

            int m = i + 1;
            String newfileName = fileName.substring(0, fileName.lastIndexOf(".")) + m + fileName.substring(fileName.lastIndexOf("."));
            String newfileFullName = filePath + newfileName;
            while (new File(newfileFullName).exists()) {
                new File(newfileFullName).delete();
            }
            FileOutputStream fos = new FileOutputStream(newfileFullName);
            FileChannel outputChannel = fos.getChannel();
            inputChannel.transferTo(startPosition, endPosition - startPosition, outputChannel);//通道传输文件数据
            outputChannel.close();
            fos.close();
            startPosition = endPosition + 1;
            endPosition += average;
            fileList.add(newfileName);
        }
        inputChannel.close();
        fis.close();
        log.info("文件切割完成,耗时:{}秒", (new Date().getTime() - start.getTime()) / 1000);
        return fileList;
    }

    /**
     * <p>Description: 获取文件总行数</P>
     *
     * @param fileFullName
     * @return int
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2022/10/23 19:12
     */
    public static int getFileTotalCount(String fileFullName) throws Exception {
        int lines = 0;
        try {
            FileReader in = new FileReader(fileFullName);
            LineNumberReader reader = new LineNumberReader(in);
            reader.skip(Long.MAX_VALUE);
            lines = reader.getLineNumber();
            in.close();
        } catch (Exception e) {
            log.error("获取文件行数失败", e);
            throw e;
        }
        return lines;
    }


    /**
     * <p>Description: 获取文件总行数</P>
     *
     * @param filePath
     * @param fileName
     * @return int
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2022/10/23 19:12
     */
    public static int getFileTotalCount(String filePath, String fileName) throws Exception {
        return getFileTotalCount(filePath + fileName);
    }

    /**
     * <p>Description: 根据行数计算文件个数</P>
     *
     * @param filePath
     * @param fileName
     * @param rowSize
     * @return int
     * @Author:jack.zhang
     * @Version 3.0.0
     * @Date 2022/10/4 11:36
     */
    public static int getFileCountByRowSize(String filePath, String fileName, int rowSize) throws Exception {
        int totalCount = getFileTotalCount(filePath, fileName);
        int fileCount = totalCount / rowSize + (totalCount % rowSize != 0 ? 1 : 0);
        return fileCount;
    }

    public static void syncFile(String filePath, List<String> fileList) throws Exception {
        List<String> fullNameList = new ArrayList<>();
        fileList.stream().forEach(item -> {
            fullNameList.add(filePath + item);
        });
        syncFile(fullNameList);
    }

    public static void syncFile(List<String> fileList) throws Exception {
        if (System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") > -1) {
            log.warn("windows 系统下无法调用shell同步文件");
            return;
        }
        for (String file : fileList) {
            log.info("同步文件:{}", file);
            List<String> cmds = new ArrayList<>();
            cmds.add("rxync");
            cmds.add(file);
            ProcessBuilder pb = new ProcessBuilder(cmds);
            Process process = pb.start();
            process.waitFor();
            if (process.exitValue() != 0) {
                throw new SystemException("批处理分段文件同步异常");
            } else {
                log.info("文件{}同步成功", file);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        String filePath = "D:\\tmp\\risk\\";
        String fileName = "CBTS_CIF_CLIENT1.dat";
        Date start = new Date();
//        List<String> strings = splitFileByCount(filePath, fileName, 5);
//        log.info("耗时：{}毫秒", new Date().getTime() - start.getTime());
//        Date start1 = new Date();
//        splitFileBySize(filePath + fileName, 200);
//        log.info("耗时：{}毫秒", new Date().getTime() - start1.getTime());
//        int fileCountByRowSize = getFileCountByRowSize(filePath, fileName, 1000000);

//        log.info("获得文件数量:{},耗时：{}毫秒", fileCountByRowSize, new Date().getTime() - start.getTime());
        BufferedReader br = null;
        String line = "";
        File file = new File(filePath + fileName);
        br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf8"));

        while ((line = br.readLine()) != null) {
            log.info(line);
        }


    }
}
