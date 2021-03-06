package com.ndipatri.solarmonitor;

import android.os.AsyncTask;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

public class AsyncTaskSchedulerRule implements TestRule {

    private final Scheduler asyncTaskScheduler =
            Schedulers.from(AsyncTask.THREAD_POOL_EXECUTOR);

    @Override
    public Statement apply(final Statement base, Description d) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                RxJavaPlugins.setIoSchedulerHandler(
                        scheduler -> asyncTaskScheduler);
                RxJavaPlugins.setComputationSchedulerHandler(
                        scheduler -> asyncTaskScheduler);
                RxJavaPlugins.setNewThreadSchedulerHandler(
                        scheduler -> asyncTaskScheduler);

                try {
                    base.evaluate();
                } finally {
                    RxJavaPlugins.reset();
                }
            }
        };
    }
}
