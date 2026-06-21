package io.github.openground.common.dbcheck;

/**
 * 异步检查进度对象
 *
 * @author open-ground
 * @since 2026-06-18
 */
public class CheckProgress {

    private final String taskId;
    private int progress;
    private String stage;
    private String message;
    private boolean finished;
    private boolean failed;
    private String errorMessage;
    private DbCheckResult result;

    public CheckProgress(String taskId) {
        this.taskId = taskId;
        this.progress = 0;
        this.stage = "初始化";
        this.message = "任务已创建";
    }

    public synchronized void update(int progress, String stage, String message) {
        this.progress = progress;
        this.stage = stage;
        this.message = message;
    }

    public synchronized void finish(DbCheckResult result) {
        this.progress = 100;
        this.stage = "完成";
        this.message = "检查完成";
        this.finished = true;
        this.result = result;
    }

    public synchronized void fail(String errorMessage) {
        this.finished = true;
        this.failed = true;
        this.errorMessage = errorMessage;
        this.message = errorMessage;
    }

    // ===== Getters =====

    public String getTaskId() { return taskId; }
    public int getProgress() { return progress; }
    public String getStage() { return stage; }
    public String getMessage() { return message; }
    public boolean isFinished() { return finished; }
    public boolean isFailed() { return failed; }
    public String getErrorMessage() { return errorMessage; }
    public DbCheckResult getResult() { return result; }
}
