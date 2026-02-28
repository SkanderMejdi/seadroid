package com.seafile.seadroid2.framework.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkerParameters;

import com.blankj.utilcode.util.CollectionUtils;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.framework.db.entities.RepoModel;
import com.seafile.seadroid2.framework.model.BaseModel;
import com.seafile.seadroid2.framework.util.Objs;
import com.seafile.seadroid2.framework.util.SLogs;

import java.util.List;

public class MetadataSyncWorker extends BaseWorker {

    private static final String TAG = "MetadataSyncWorker";

    public MetadataSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Account account = getCurrentAccount();
        if (account == null) {
            return Result.failure();
        }

        try {
            // Sync repos list
            List<BaseModel> repos = Objs.getReposSingleFromServer(account).blockingGet();
            SLogs.d(TAG, "doWork()", "Synced " + (repos != null ? repos.size() : 0) + " repos");

            if (CollectionUtils.isEmpty(repos)) {
                return Result.success();
            }

            // Sync root dirents for each non-encrypted repo
            for (BaseModel baseModel : repos) {
                if (!(baseModel instanceof RepoModel repoModel)) {
                    continue;
                }

                if (repoModel.encrypted) {
                    continue;
                }

                try {
                    Objs.getDirentsSingleFromServer(account, repoModel.repo_id, repoModel.repo_name, "/").blockingGet();
                } catch (Exception e) {
                    SLogs.d(TAG, "doWork()", "Failed to sync dirents for repo: " + repoModel.repo_name);
                }
            }

            return Result.success();
        } catch (Exception e) {
            SLogs.d(TAG, "doWork()", "Metadata sync failed: " + e.getMessage());
            return Result.retry();
        }
    }
}
