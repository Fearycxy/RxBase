/**
 * Created by feary on 18-4-27.
 */

/*
 RxBase 使用方法
*  RxBase.create(new RxBase.Callback<Object>() {

                    @Override
                    public Object run() {
                    //此处为耗时方法
                        return null;
                    }

                    @Override
                    public void onNext(Object o) {
                         //此处为主线程回调
                    }
                }).compose(bindToLifecycle()).subScribe();
                //默认io运行，主线程回调，也可指定其他线程
* */
public class RxBase<T> {
    private Callback<T> callback;
    private WrapCallBack wrapCallBack;
    private Observable.Transformer<T, T> transformer;
    private Scheduler subsriribeThread = Schedulers.io();
    private Scheduler observerThread = AndroidSchedulers.mainThread();

    private RxBase(@NonNull Callback<T> callback) {
        this.callback = callback;
    }

    public static <T> RxBase<T> create(@NonNull Callback<T> callback) {
        return new RxBase<T>(callback);
    }

    public RxBase compose(Observable.Transformer<T, T> transformer) {
        this.transformer = transformer;
        return this;
    }

    public RxBase setOnError(WrapCallBack wrapCallBack) {
        this.wrapCallBack = wrapCallBack;
        return this;
    }

    public final RxBase<T> subscribeOn(Scheduler scheduler) {
        subsriribeThread = scheduler;
        return this;
    }

    public final RxBase<T> observeOn(Scheduler scheduler) {
        subsriribeThread = scheduler;
        return this;
    }

    public void subScribe() {
        Observable<T> observable = Observable.create(new Observable.OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                subscriber.onNext(callback.run());
                subscriber.onCompleted();
            }
        }).subscribeOn(subsriribeThread)
                .observeOn(observerThread);
        if (transformer != null) {
            observable.compose(transformer);
        }
        observable.subscribe(new Subscriber<T>() {

            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                if (wrapCallBack != null) {
                    wrapCallBack.onError(e);
                }
            }

            @Override
            public void onNext(T o) {
                callback.onNext(o);
            }
        });

    }

    public interface Callback<T> {
        T run();

        void onNext(T t);
    }

    public interface WrapCallBack {
        void onError(Throwable e);
    }
}
