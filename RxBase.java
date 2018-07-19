package com.base.utils.rx;

import android.support.annotation.NonNull;
import android.telecom.Call;

import com.baidu.platform.comapi.map.C;
import com.base.common.BuildConfig;
import com.base.log.MyLog;
import com.base.utils.Constants;
import com.base.utils.toast.ToastUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by feary on 18-4-27.
 */

/**
 * RxBase 使用方法
 * RxBase.create(new RxBase.Callback<Object>() {
 *
 * @Override public Object run() {
 * //此处为耗时方法
 * return null;
 * }
 * @Override public void onNext(Object o) {
 * //此处为主线程回调
 * }
 * //目前想到的是回调onError直接throw {@link RxException}
 * }).bindLife(this).letsgo();
 * //默认io运行，主线程回调，也可指定其他线程
 */
public class RxBase<T> {
    private Callback<T> callback;
    private Scheduler subscribeThread = Schedulers.io();
    private Scheduler observerThread = AndroidSchedulers.mainThread();
    private Observable<T> observable;

    private RxBase(@NonNull Callback<T> callback) {
        this.callback = callback;
        observable = Observable.create(subscriber -> {
            try {
                T t = RxBase.this.callback.run();
                subscriber.onNext(t);
            } catch (RxException e) {
                subscriber.onError(e);
            } catch (Exception e) {
                MyLog.e("RxBase onError intercept: " + e);
                subscriber.onError(e);
            } finally {
                subscriber.onCompleted();
            }
        });
    }

    public static <T> RxBase<T> create(@NonNull Callback<T> callback) {
        return new RxBase<>(callback);
    }

    public final <R> RxBase<R> lift(final Observable.Operator<? extends R, ? super T> operator) {
        return new RxBase<R>(observable.lift(operator));
    }

    public final <U> RxBase<T> distinct(Func1<? super T, ? extends U> keySelector) {
        return new RxBase<T>(observable.distinct(keySelector));
    }

    public static <T> RxBase<T> from(@NonNull Iterable<? extends T> iterable) {
        return new RxBase<T>(Observable.from(iterable));
    }

    private RxBase(Observable observable) {
        this.observable = observable;
    }

    public final <R> RxBase<R> flatMap(Func1<? super T, ? extends Observable<? extends R>> func) {
        return new RxBase<R>(observable.flatMap(func));
    }

    public final RxBase<T> delay(long delay, TimeUnit unit) {
        observable.delay(delay, unit);
        return this;
    }

    public final RxBase<T> bindLife(@NonNull RxLife f) {
        observable.compose(f.bindUntilEvent());
        return this;
    }

    public final RxBase<List<T>> buffer(int count) {
        return new RxBase<List<T>>(observable.buffer(count));
    }


    public final RxBase<T> retry(int times) {
        observable.retryWhen(new RxRetryAssist(times, "retry exceed " + times + " times"));
        return this;
    }

    /**
     * 直接调用上面的bindLife
     *
     * @see #bindLife(RxLife)
     */
    public final RxBase<T> compose(Observable.Transformer<T, T> transformer) {
        observable.compose(transformer);
        return this;
    }

    public final RxBase<T> subscribeOn(Scheduler scheduler) {
        subscribeThread = scheduler;
        return this;
    }

    public final RxBase<T> observeOn(Scheduler scheduler) {
        observerThread = scheduler;
        return this;
    }

    public final RxBase<T> filter(Func1<? super T, Boolean> predicate) {
        observable.filter(predicate);
        return this;
    }

    public Subscription letsgo() {
        return subscribe();
    }

    public Subscription subscribe() {
        return observable.subscribeOn(subscribeThread)
                .observeOn(observerThread)
                .subscribe(new Subscriber<T>() {

                    @Override
                    public void onCompleted() {
                        if (callback != null) {
                            callback.onCompleted();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (callback != null) {
                            callback.onError(e);
                        } else {
                            baseError(e);
                        }
                    }

                    @Override
                    public void onNext(T t) {
                        if (callback != null) {
                            callback.onNext(t);
                        }
                    }
                });
    }

    public Subscription subscribe(@NonNull Action1<T> action1) {
        return observable.subscribeOn(subscribeThread)
                .observeOn(observerThread)
                .subscribe(new Subscriber<T>() {

                    @Override
                    public void onCompleted() {
                        if (callback != null) {
                            callback.onCompleted();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (callback != null) {
                            callback.onError(e);
                        } else {
                            baseError(e);
                        }
                    }

                    @Override
                    public void onNext(T t) {
                        action1.call(t);
                    }
                });
    }

    public final RxBase<List<T>> toList() {
        return new RxBase<List<T>>(observable.toList());
    }


    public Subscription subscribe(@NonNull Observer<T> observer) {
        return observable.subscribeOn(subscribeThread)
                .observeOn(observerThread)
                .subscribe(new Subscriber<T>() {

                    @Override
                    public void onCompleted() {
                        observer.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        observer.onError(e);
                    }

                    @Override
                    public void onNext(T t) {
                        observer.onNext(t);
                    }
                });
    }

    public final <R> RxBase<R> map(Func1<? super T, ? extends R> func) {
        return new RxBase<R>(observable.map(func));
    }

    public Subscription subscribe(@NonNull Action1<T> next, @NonNull Action1<Throwable> error) {
        return observable.subscribeOn(subscribeThread)
                .observeOn(observerThread)
                .subscribe(new Subscriber<T>() {

                    @Override
                    public void onCompleted() {
                        if (callback != null) {
                            callback.onCompleted();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        error.call(e);
                    }

                    @Override
                    public void onNext(T t) {
                        next.call(t);
                    }
                });
    }

    public Subscription subscribe(@NonNull Action1<T> next, @NonNull Action1<Throwable> error, @NonNull Action0 complete) {
        return observable.subscribeOn(subscribeThread)
                .observeOn(observerThread)
                .subscribe(new Subscriber<T>() {

                    @Override
                    public void onCompleted() {
                        complete.call();
                    }

                    @Override
                    public void onError(Throwable e) {
                        error.call(e);
                    }

                    @Override
                    public void onNext(T t) {
                        next.call(t);
                    }
                });
    }

    private static void baseError(Throwable e) {
        MyLog.e("RxBase exception!!!!!: \n\t: ", e);
        if (Constants.isDebugBuild || BuildConfig.DEBUG) {
            ToastUtils.showToast("RxBase崩溃啦啦啦啦！： " + e.getCause());
            throw new RxException(e);
        }
    }

    public interface Callback<T> {
        T run();

        void onNext(T t);

        default void onError(Throwable e) {
            baseError(e);
        }

        default void onCompleted() {
        }
    }

    public static abstract class Task<T> implements Callback<T> {
        @Override
        public T run() {
            return null;
        }

        @Override
        public void onNext(T o) {

        }
    }

}
