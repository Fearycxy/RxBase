package com.base.utils.rx;

import android.support.annotation.NonNull;
import android.view.View;

import com.base.common.BuildConfig;
import com.base.log.MyLog;
import com.base.utils.Constants;
import com.base.utils.toast.ToastUtils;
import com.jakewharton.rxbinding.view.RxView;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.annotations.Experimental;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.internal.operators.OnSubscribeFromCallable;
import rx.internal.operators.OnSubscribeFromIterable;
import rx.internal.operators.OnSubscribeTimerPeriodically;
import rx.internal.operators.OnSubscribeToObservableFuture;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

/**
 * Created by feary on 18-4-27.
 */

/**
 * RxBase 使用方法
 * RxBase.post({this is your equation});
 * 如希望调用OnError 直接throw {@link RxException}
 * 但切记，如果希望回调{@link Callback#onError(Throwable)}，一定要实现ta ,不然你这样的操作没有意义，我会直接throw出去
 * 如果有需要Rx的其他操作符，直接加在这里，
 * 如果有其他自定义方法，建议新建类继承此类，类名写在这里{@link}，不要都放在这一个类里，以免耦合
 */
public final class RxBase<T> {
    private Callback<T> callback;
    private Observable<T> observable;
    private boolean isCallDefault = false;

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

    /**
     * io运行，主线程回调
     *
     * @param callback
     * @param <T>
     */
    public static <T> Subscription post(Callback<T> callback) {
        return RxBase.create(callback).defaultCall();
    }

    public static <T> Subscription post(Callback<T> callback, @NonNull RxLife rxLife) {
        return RxBase.create(callback).bindLife(rxLife).defaultCall();
    }

    /**
     * 主线程回调
     *
     * @param runnable
     */
    public static Subscription post(Runnable runnable) {
        return post(runnable, null);
    }

    public static Subscription post(Runnable runnable, RxLife rxLife) {
        RxBase base = RxBase.create(new Task<Object>() {
            @Override
            public void onNext(Object o) {
                runnable.run();
            }
        });
        if (rxLife != null) {
            base.bindLife(rxLife);
        }
        return base.defaultCall();
    }

    /**
     * IO线程回调
     *
     * @param runnable
     */
    public static Subscription postIo(Runnable runnable) {
        return postIo(runnable, null);
    }

    public final RxBase<T> throttleFirst(long windowDuration, TimeUnit unit) {
        observable = observable.throttleFirst(windowDuration, unit);
        return this;
    }

    public final <E> RxBase<T> takeUntil(Observable<? extends E> other) {
        return new RxBase<T>(observable.takeUntil(other));
    }

    public static RxBase<Void> clicks(@NonNull View view) {
        return new RxBase<Void>(RxView.clicks(view));
    }

    public final static <T> RxBase<T> from(Future<? extends T> future) {
        return new RxBase<T>(Observable.from(future));
    }

    public final static <T> RxBase<T> from(Future<? extends T> future, long timeout, TimeUnit unit) {
        return new RxBase<T>(Observable.from(future, timeout, unit));
    }

    public final static <T> RxBase<T> from(Future<? extends T> future, Scheduler scheduler) {
        return new RxBase<T>(Observable.from(future, scheduler));
    }

    public final static <T> RxBase<T> from(T[] array) {
        return new RxBase<T>(Observable.from(Arrays.asList(array)));
    }

    @Experimental
    public static <T> RxBase<T> fromCallable(Callable<? extends T> func) {
        return new RxBase<T>(Observable.fromCallable(func));
    }

    public final static RxBase<Long> interval(long interval, TimeUnit unit) {
        return interval(interval, interval, unit, Schedulers.computation());
    }

    public final static RxBase<Long> interval(long interval, TimeUnit unit, Scheduler scheduler) {
        return interval(interval, interval, unit, scheduler);
    }

    public final static RxBase<Long> interval(long initialDelay, long period, TimeUnit unit) {
        return interval(initialDelay, period, unit, Schedulers.computation());
    }

    public final static RxBase<Long> interval(long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        return new RxBase<Long>(Observable.interval(initialDelay, period, unit, scheduler));
    }

    public static Subscription postIo(Runnable runnable, RxLife rxLife) {
        RxBase base = RxBase.create(new Task<Object>() {
            @Override
            public Object run() {
                runnable.run();
                return null;
            }
        });
        if (rxLife != null) {
            base.bindLife(rxLife);
        }
        return base.defaultCall();
    }

    /**
     * 此方法最后结束时默认线程请使用{@link #defaultCall()}，如自定义线程请使用{@link #subscribe()}
     * <p>
     * 如调用流转换， 请使用结束操作符为{@link #subscribe(Action1),#subscribe(Action1, Action1),#subscribe(Action1, Action1, Action0)}
     * <p>
     * 但此时，对应的Create的Callback里的{@link Callback#onNext(Object),Callback#onError(Throwable),Callback#onCompleted()}无效，会直接调用最后传入的调用
     * <p>
     * 如不想写不实现的方法，可使用{@link Task}简化
     */
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
        observable = observable.delay(delay, unit);
        return this;
    }

    public final RxBase<T> bindLife(@NonNull RxLife f) {
        observable = observable.compose(f.bindUntilEvent());
        return this;
    }

    public final RxBase<List<T>> buffer(int count) {
        return new RxBase<List<T>>(observable.buffer(count));
    }


    public final RxBase<T> retry(int times) {
        observable = observable.retryWhen(new RxRetryAssist(times, "retry exceed " + times + " times"));
        return this;
    }

    public final RxBase<T> retryWhen(final Func1<? super Observable<? extends Throwable>, ? extends Observable<?>> notificationHandler) {
        observable = observable.retryWhen(notificationHandler);
        return this;
    }

    /**
     * 直接调用上面的bindLife
     *
     * @see #bindLife(RxLife)
     */
    public final RxBase<T> compose(Observable.Transformer<T, T> transformer) {
        observable = observable.compose(transformer);
        return this;
    }

    public final RxBase<T> subscribeOn(Scheduler scheduler) {
        isCallDefault = true;
        observable = observable.subscribeOn(scheduler);
        return this;
    }

    public final RxBase<T> observeOn(Scheduler scheduler) {
        isCallDefault = true;
        observable = observable.observeOn(scheduler);
        return this;
    }

    public final RxBase<T> filter(Func1<? super T, Boolean> predicate) {
        observable = observable.filter(predicate);
        return this;
    }

    /**
     * 此方法io运行，主线程回调，不可更改，更改直接崩溃！！！！！！！
     * <p>
     * 后面几个subscribe方法没有默认线程调用的操作，完全与RXjava相同，复杂操作请使用{@link #subscribe}
     * <p>
     * 切忌，此方法仅适合调用{@link #filter(Func1),#compose(Observable.Transformer),#bindLife(RxLife),#retry(int)}等简易的无流变化的操作符，
     * <p>
     * !!!!!!!不可自定义调用线程{@link #observeOn(Scheduler) {@link #subscribeOn(Scheduler)}}!!!!!!!!! 调用会崩溃
     * <p>
     * 调用了{@link #flatMap(Func1),#lift(Observable.Operator),#buffer(int),#distinct(Func1)}等流转换的操作符，不要调用此方法，以免混淆
     * <p>
     * <p>
     * 上面所有复杂的自定义需求请最好调用{@link #subscribe(),#subscribe(Action1),#subscribe(Action1, Action1)}，这几个方法没有自定义默认线程策略，完全跟RX一致
     */
    public Subscription defaultCall() {
        if (isCallDefault)
            throw new RxException("Please obey the RXBase Rule: do not call defaultCall and with thread or flatMap or map or lift and so on!!!!");
        return subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public Subscription subscribe() {
        return observable.subscribe(new Subscriber<T>() {

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
        return observable.subscribe(new Subscriber<T>() {

            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                baseError(e);
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

    public static <T> RxBase<T> just(final T value) {
        return new RxBase<T>(Observable.just(value));
    }

    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public static <T> RxBase<T> just(T t1, T t2) {
        return from(Arrays.asList(t1, t2));
    }

    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public static <T> RxBase<T> just(T t1, T t2, T t3) {
        return from(Arrays.asList(t1, t2, t3));
    }

    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public static <T> RxBase<T> just(T t1, T t2, T t3, T t4) {
        return from(Arrays.asList(t1, t2, t3, t4));
    }

    public Subscription subscribe(@NonNull Observer<T> observer) {
        return observable.subscribe(observer);
    }

    public final <R> RxBase<R> map(Func1<? super T, ? extends R> func) {
        return new RxBase<R>(observable.map(func));
    }

    public Subscription subscribe(@NonNull Action1<T> next, @NonNull Action1<Throwable> error) {
        return observable.subscribe(next, error);
    }

    public Subscription subscribe(@NonNull Action1<T> next, @NonNull Action1<Throwable> error, @NonNull Action0 complete) {
        return observable
                .subscribe(next, error, complete);
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

        /**
         * 如果使用流转换操作符，则这里的实现是无效的，因为这个已经被包了一层，
         * 同样无效的是onNext和onCompleted，本来要转换你要还这样写也没什么意义
         *
         * @return
         */
        default void onError(Throwable e) {
            baseError(e);
        }

        default void onCompleted() {
        }
    }

    public interface Task<T> extends Callback<T> {
        @Override
        default T run() {
            return null;
        }

        @Override
        default void onNext(T o) {

        }
    }

}
