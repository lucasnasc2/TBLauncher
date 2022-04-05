package rocks.tbog.tblauncher.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintHelper;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.Lifecycle;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import rocks.tbog.tblauncher.WorkAsync.AsyncTask;
import rocks.tbog.tblauncher.WorkAsync.RunnableTask;
import rocks.tbog.tblauncher.WorkAsync.TaskRunner;
import rocks.tbog.tblauncher.result.ResultViewHelper;
import rocks.tbog.tblauncher.ui.CenteredImageSpan;
import rocks.tbog.tblauncher.ui.CutoutFactory;
import rocks.tbog.tblauncher.ui.ICutout;
import rocks.tbog.tblauncher.ui.ViewStubPreview;

public class Utilities {
    public final static ExecutorService EXECUTOR_RUN_ASYNC;
    private final static int[] ON_SCREEN_POS = new int[2];
    private final static Rect ON_SCREEN_RECT = new Rect();
    private static final String TAG = "TBUtil";

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = 10;
    private static final int KEEP_ALIVE_SECONDS = 3;
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "UtilAsync #" + mCount.getAndIncrement());
        }
    };

    private static final Class<?> CLASS_GRADIENT_DRAWABLE_GRADIENT_STATE;
    private static final Field GRADIENT_DRAWABLE_FIELD_GRADIENT_STATE;
    private static final Field GRADIENT_DRAWABLE_GRADIENT_STATE_FIELD_POSITIONS;

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(), sThreadFactory);
        threadPoolExecutor.setRejectedExecutionHandler((runnable, executor) -> {
            Log.w(TAG, "task rejected");
            if (!executor.isShutdown()) {
                runnable.run();
            }
        });
        EXECUTOR_RUN_ASYNC = threadPoolExecutor;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            CLASS_GRADIENT_DRAWABLE_GRADIENT_STATE = null;
            GRADIENT_DRAWABLE_FIELD_GRADIENT_STATE = null;
            GRADIENT_DRAWABLE_GRADIENT_STATE_FIELD_POSITIONS = null;
        } else {
            // make mGradientState accessible
            Field f_mGradientState = null;
            try {
                f_mGradientState = GradientDrawable.class.getDeclaredField("mGradientState");
                f_mGradientState.setAccessible(true);
            } catch (Throwable t) {
                Log.w(TAG, "make mGradientState from " + GradientDrawable.class.getSimpleName() + " accessible", t);
            }
            GRADIENT_DRAWABLE_FIELD_GRADIENT_STATE = f_mGradientState;

            // make mGradientState.mPositions accessible
            Class<?> c_GradientState = null;
            try {
                c_GradientState = Class.forName(GradientDrawable.class.getName() + "$GradientState");
            } catch (ClassNotFoundException ignored) {
            }
            CLASS_GRADIENT_DRAWABLE_GRADIENT_STATE = c_GradientState;
            Field f_mPositions = null;
            if (c_GradientState != null) {
                try {
                    f_mPositions = c_GradientState.getDeclaredField("mPositions");
                    f_mPositions.setAccessible(true);
                } catch (Throwable t) {
                    Log.w(TAG, "make GradientState.mPositions from " + c_GradientState + " accessible", t);
                }
            }
            GRADIENT_DRAWABLE_GRADIENT_STATE_FIELD_POSITIONS = f_mPositions;
        }
    }

    // https://stackoverflow.com/questions/3035692/how-to-convert-a-drawable-to-a-bitmap
    @NonNull
    public static Bitmap drawableToBitmap(@Nullable Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        if (drawable != null) {
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        } else {
            canvas.drawRGB(255, 255, 255);
        }
        return bitmap;
    }

    @Nullable
    public static byte[] bitmapToByteArray(@NonNull Bitmap bitmap) {
        ByteArrayOutputStream stream = null;
        try {
            stream = new ByteArrayOutputStream(1024);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                stream = null;
            else {
                stream.flush();
                stream.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to convert bitmap", e);
            stream = null;
        }
        return stream != null ? stream.toByteArray() : null;
    }

    /**
     * Returns a drawable suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    public static Drawable createIconDrawable(Intent.ShortcutIconResource iconRes, Context context) {
        PackageManager packageManager = context.getPackageManager();
        // the resource
        try {
            Resources resources = packageManager.getResourcesForApplication(iconRes.packageName);
            final int id = resources.getIdentifier(iconRes.resourceName, null, null);
            return ResourcesCompat.getDrawableForDensity(resources, id, 0, null);
        } catch (Exception e) {
            // Icon not found.
        }
        return null;
    }

    /**
     * Returns a drawable which is of the appropriate size to be displayed as an icon
     */
    public static Drawable createIconDrawable(Bitmap icon, Context context) {
        return new BitmapDrawable(context.getResources(), icon);
    }

    @NonNull
    public static ICutout getNotchCutout(Activity activity) {
        ICutout cutout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            cutout = CutoutFactory.getForAndroidPie(activity);
        else
            cutout = CutoutFactory.getByManufacturer(activity, Build.MANUFACTURER);

        return cutout == null ? CutoutFactory.getNoCutout() : cutout;
    }

    public static void setIconAsync(@NonNull ImageView image, @NonNull GetDrawable callback) {
        TaskRunner.executeOnExecutor(ResultViewHelper.EXECUTOR_LOAD_ICON,
            new Utilities.AsyncSetDrawable(image) {
                @Override
                protected Drawable getDrawable(Context context) {
                    return callback.getDrawable(context);
                }
            }
        );
    }

    public static void setViewAsync(@NonNull View image, @NonNull GetDrawable cbGet, @NonNull SetDrawable cbSet) {
        TaskRunner.executeOnExecutor(ResultViewHelper.EXECUTOR_LOAD_ICON,
            new Utilities.AsyncViewSet(image) {
                @Override
                protected Drawable getDrawable(Context context) {
                    return cbGet.getDrawable(context);
                }

                @Override
                protected void setDrawable(@NonNull View view, @NonNull Drawable drawable) {
                    cbSet.setDrawable(view, drawable);
                }
            }
        );
    }

    public static void setIntentSourceBounds(@NonNull Intent intent, @Nullable View v) {
        if (v == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            v.getLocationOnScreen(ON_SCREEN_POS);
            ON_SCREEN_RECT.set(ON_SCREEN_POS[0], ON_SCREEN_POS[1], ON_SCREEN_POS[0] + v.getWidth(), ON_SCREEN_POS[1] + v.getHeight());
            intent.setSourceBounds(ON_SCREEN_RECT);
        }
    }

    @Nullable
    public static Bundle makeStartActivityOptions(@Nullable View source) {
        if (source == null)
            return null;
        Bundle opts = null;
        // If we got an icon, we create options to get a nice animation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            opts = ActivityOptions.makeClipRevealAnimation(source, 0, 0, source.getMeasuredWidth(), source.getMeasuredHeight()).toBundle();
        }
        if (opts == null) {
            opts = ActivityOptions.makeScaleUpAnimation(source, 0, 0, source.getMeasuredWidth(), source.getMeasuredHeight()).toBundle();
        }
        return opts;
    }

    @Nullable
    public static Rect getOnScreenRect(@Nullable View v) {
        if (v == null)
            return null;
        v.getLocationOnScreen(ON_SCREEN_POS);
        ON_SCREEN_RECT.set(ON_SCREEN_POS[0], ON_SCREEN_POS[1], ON_SCREEN_POS[0] + v.getWidth(), ON_SCREEN_POS[1] + v.getHeight());
        return ON_SCREEN_RECT;
    }

    public static boolean checkFlag(int flags, int flagToCheck) {
        return (flags & flagToCheck) == flagToCheck;
    }

    public static boolean checkAnyFlag(int flags, int anyFlag) {
        return (flags & anyFlag) != 0;
    }

    /**
     * Return a valid activity or null given a view
     *
     * @param view any view of an activity
     * @return an activity or null
     */
    @Nullable
    public static Activity getActivity(@Nullable View view) {
        return view != null ? getActivity(view.getContext()) : null;
    }

    /**
     * Return a valid activity or null given a context
     *
     * @param ctx context
     * @return an activity or null
     */
    @Nullable
    public static Activity getActivity(@Nullable Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                Activity act = (Activity) ctx;
                if (act.isFinishing() || act.isDestroyed())
                    return null;
                return act;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

//    public static void positionToast(@NonNull Toast toast, @NonNull View anchor, int offsetX, int offsetY) {
//        // toasts are positioned relatively to decor view, views relatively to their parents, we have to gather additional data to have a common coordinate system
//        Rect rect = new Rect();
//        anchor.getWindowVisibleDisplayFrame(rect);
//
//        // covert anchor view absolute position to a position which is relative to decor view
//        int[] viewLocation = new int[2];
//        anchor.getLocationOnScreen(viewLocation);
//        int viewLeft = viewLocation[0] - rect.left;
//        int viewTop = viewLocation[1] - rect.top;
//
//        // measure toast to center it relatively to the anchor view
//        DisplayMetrics metrics = new DisplayMetrics();
//        anchor.getDisplay().getMetrics(metrics);
//        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.UNSPECIFIED);
//        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.UNSPECIFIED);
//        toast.getView().measure(widthMeasureSpec, heightMeasureSpec);
//        int toastWidth = toast.getView().getMeasuredWidth();
//
//        // compute toast offsets
//        int toastX = viewLeft + (anchor.getWidth() - toastWidth) / 2 + offsetX;
//        int toastY = viewTop + anchor.getHeight() + offsetY;
//
//        toast.setGravity(Gravity.START | Gravity.TOP, toastX, toastY);
//    }

    public static RunnableTask runAsync(@NonNull Lifecycle lifecycle, @NonNull TaskRunner.AsyncRunnable background, @NonNull TaskRunner.AsyncRunnable after) {
        RunnableTask task = TaskRunner.newTask(lifecycle, background, after);
        TaskRunner.runOnUiThread(() -> EXECUTOR_RUN_ASYNC.execute(task));
        return task;
    }

    public static RunnableTask runAsync(@NonNull TaskRunner.AsyncRunnable background, @NonNull TaskRunner.AsyncRunnable after) {
        RunnableTask task = TaskRunner.newTask(background, after);
        TaskRunner.runOnUiThread(() -> EXECUTOR_RUN_ASYNC.execute(task));
        return task;
    }

    public static void runAsync(@NonNull Runnable background) {
        EXECUTOR_RUN_ASYNC.execute(background);
    }

    public static <I, O> void executeAsync(@NonNull AsyncTask<I, O> task) {
        TaskRunner.executeOnExecutor(EXECUTOR_RUN_ASYNC, task);
    }

    public static void setColorFilterMultiply(@NonNull ImageView imageView, int color) {
        setColorFilterMultiply(imageView.getDrawable(), color);
    }

    public static void setColorFilterMultiply(@Nullable Drawable drawable, int color) {
        if (drawable == null)
            return;
        ColorFilter cf = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        drawable.setColorFilter(cf);
    }

    @SuppressLint("ObsoleteSdkInt")
    public static void expandNotificationsPanel(Activity activity) {
        @SuppressLint("WrongConstant")
        Object statusBarService = activity.getSystemService("statusbar");
        if (statusBarService != null) {
            try {
                Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                Method expand;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    expand = statusbarManager.getMethod("expandNotificationsPanel");
                } else {
                    expand = statusbarManager.getMethod("expand");
                }
                expand.setAccessible(true);
                expand.invoke(statusBarService);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    public static void expandSettingsPanel(Activity activity) {
        boolean expandCalled = false;
        @SuppressLint("WrongConstant")
        Object statusBarService = activity.getSystemService("statusbar");
        if (statusBarService != null) {
            try {
                Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                Method expand;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    expand = statusbarManager.getMethod("expandSettingsPanel");
                    expand.setAccessible(true);
                    expand.invoke(statusBarService);
                    expandCalled = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (!expandCalled) {
            Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
            activity.startActivity(settings);
        }
    }

    public static void setVerticalScrollbarThumbDrawable(View scrollView, Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scrollView.setVerticalScrollbarThumbDrawable(drawable);
        } else {
            try {
                //noinspection JavaReflectionMemberAccess
                Field mScrollCacheField = View.class.getDeclaredField("mScrollCache");
                mScrollCacheField.setAccessible(true);
                Object mScrollCache = mScrollCacheField.get(scrollView);
                Field scrollBarField = mScrollCache.getClass().getDeclaredField("scrollBar");
                scrollBarField.setAccessible(true);
                Object scrollBar = scrollBarField.get(mScrollCache);
                Method method = scrollBar.getClass().getDeclaredMethod("setVerticalThumbDrawable", Drawable.class);
                method.setAccessible(true);
                method.invoke(scrollBar, drawable);
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean classContainsDeclaredField(@NonNull Class<?> objectClass, @NonNull String fieldName) {
        for (Field field : objectClass.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    public static void setTextCursorDrawable(@NonNull TextView editText, Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.setTextCursorDrawable(drawable);
        } else {
            boolean setResToNull = false;
            if (classContainsDeclaredField(TextView.class, "mCursorDrawable")) {
                try {
                    @SuppressLint("BlockedPrivateApi")
                    Field fmCursorDrawable = TextView.class.getDeclaredField("mCursorDrawable");
                    fmCursorDrawable.setAccessible(true);
                    fmCursorDrawable.set(editText, drawable);
                    setResToNull = true;
                } catch (Throwable t) {
                    Log.w(TAG, "set TextView mCursorDrawable", t);
                }
            }
            if (classContainsDeclaredField(TextView.class, "mCursorDrawableRes")) {
                try {
                    Field fmCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                    fmCursorDrawableRes.setAccessible(true);
                    if (setResToNull)
                        fmCursorDrawableRes.setInt(editText, 0);
                    else if (fmCursorDrawableRes.getInt(editText) == 0) {
                        // this resource will not get used, we just need something != 0
                        int res = android.R.drawable.divider_horizontal_dark;
                        fmCursorDrawableRes.setInt(editText, res);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "set TextView mCursorDrawableRes", t);
                }
            }
            //https://github.com/aosp-mirror/platform_frameworks_base/blob/c46c4a6765196bcabf3ea89771a1f9067b22baad/core/java/android/widget/TextView.java#L4587
            if (classContainsDeclaredField(TextView.class, "mEditor")) {
                Object mEditor = null;
                try {
                    Field fmEditor = TextView.class.getDeclaredField("mEditor");
                    fmEditor.setAccessible(true);
                    mEditor = fmEditor.get(editText);
                } catch (Throwable t) {
                    Log.w(TAG, "get TextView mEditor", t);
                }
                if (mEditor == null)
                    return;
                if (classContainsDeclaredField(mEditor.getClass(), "mCursorDrawable")) {
                    try {
                        Field fmCursorDrawable = mEditor.getClass().getDeclaredField("mCursorDrawable");
                        fmCursorDrawable.setAccessible(true);
                        fmCursorDrawable.set(mEditor, new Drawable[]{drawable, drawable});
                    } catch (Throwable t) {
                        Log.w(TAG, "set Editor mCursorDrawable[2]", t);
                    }
                }
            }
        }
    }

    private static Drawable getDrawableFromTextViewEditor(@NonNull TextView view, @NonNull String editorField) {
        Drawable drawable = null;
        Object editor = null;
        try {
            Field f_editor = TextView.class.getDeclaredField("mEditor");
            f_editor.setAccessible(true);
            editor = f_editor.get(view);
        } catch (Throwable t) {
            Log.w(TAG, "get Editor from " + view.getClass(), t);
        }
        if (editor != null && classContainsDeclaredField(editor.getClass(), editorField)) {
            try {
                Field f_handle = editor.getClass().getDeclaredField(editorField);
                f_handle.setAccessible(true);
                if (f_handle.getType().isArray()) {
                    Object drawables = f_handle.get(editor);
                    drawable = ((Drawable[]) drawables)[0];
                } else {
                    drawable = (Drawable) f_handle.get(editor);
                }
            } catch (Throwable t) {
                Log.w(TAG, "get `" + editorField + "` from " + editor.getClass(), t);
            }
        }
        return drawable;
    }

    @Nullable
    private static Drawable getDrawableFromTextView(@NonNull TextView view, @NonNull String fieldName, @NonNull String editorField) {
        Context ctx = view.getContext();
        String resFieldName = fieldName + "Res";
        if (classContainsDeclaredField(TextView.class, resFieldName)) {
            try {
                Field f_res = TextView.class.getDeclaredField(resFieldName);
                f_res.setAccessible(true);
                int res = f_res.getInt(view);
                if (res != Resources.ID_NULL) {
                    Drawable drawable = AppCompatResources.getDrawable(ctx, res);
                    if (drawable != null)
                        return drawable;
                }
            } catch (Throwable t) {
                Log.w(TAG, "get `" + resFieldName + "` from " + TextView.class, t);
            }
        }
        if (classContainsDeclaredField(TextView.class, fieldName)) {
            try {
                Field f_drawable = TextView.class.getDeclaredField(fieldName);
                f_drawable.setAccessible(true);
                Drawable drawable = (Drawable) f_drawable.get(view);
                if (drawable != null)
                    return drawable;
            } catch (Throwable t) {
                Log.w(TAG, "get `" + fieldName + "` from " + TextView.class, t);
            }
        }

        return getDrawableFromTextViewEditor(view, editorField);
    }

    public static void setTextCursorColor(@NonNull TextView editText, @ColorInt int color) {
        Context ctx = editText.getContext();
        Drawable drawable = getDrawableFromTextView(editText, "mCursorDrawable", "mCursorDrawable");
        if (drawable == null) {
            drawable = new ShapeDrawable(new RectShape());
            ((ShapeDrawable) drawable).setIntrinsicWidth(UISizes.dp2px(ctx, 2));
            ((ShapeDrawable) drawable).getPaint().setColor(color);
        }
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        setTextCursorDrawable(editText, drawable);
    }

    private static void setTextSelectHandle(@NonNull TextView editText, @NonNull String fieldName, @NonNull String editorField, Drawable drawable) {
        String fieldNameRes = fieldName + "Res";
        if (classContainsDeclaredField(TextView.class, fieldNameRes)) {
            try {
                Field f_handleRes = TextView.class.getDeclaredField(fieldNameRes);
                f_handleRes.setAccessible(true);
                f_handleRes.setInt(editText, 0);
            } catch (Throwable t) {
                Log.w(TAG, "set `" + fieldNameRes + "` from " + editText.getClass(), t);
            }
        }
        if (classContainsDeclaredField(TextView.class, fieldName)) {
            try {
                Field f_handle = TextView.class.getDeclaredField(fieldName);
                f_handle.setAccessible(true);
                f_handle.set(editText, drawable);
            } catch (Throwable t) {
                Log.w(TAG, "set `" + fieldName + "` from " + editText.getClass(), t);
            }
        }
        if (!classContainsDeclaredField(TextView.class, "mEditor"))
            return;
        Object editor = null;
        try {
            Field f_editor = TextView.class.getDeclaredField("mEditor");
            f_editor.setAccessible(true);
            editor = f_editor.get(editText);
        } catch (Throwable t) {
            Log.w(TAG, "get Editor from " + editText.getClass(), t);
        }
        if (editor == null)
            return;
        try {
            Field f_handle = editor.getClass().getDeclaredField(editorField);
            f_handle.setAccessible(true);
            f_handle.set(editor, drawable);
        } catch (Throwable t) {
            Log.w(TAG, "set `" + editorField + "` from " + editor.getClass(), t);
        }
    }

    public static void setTextSelectHandle(@NonNull TextView editText, Drawable left, Drawable right, Drawable center) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.setTextSelectHandle(center);
            editText.setTextSelectHandleLeft(left);
            editText.setTextSelectHandleRight(right);
        } else {
            setTextSelectHandle(editText, "mTextSelectHandleLeft", "mSelectHandleLeft", left);
            setTextSelectHandle(editText, "mTextSelectHandleRight", "mSelectHandleRight", right);
            setTextSelectHandle(editText, "mTextSelectHandle", "mSelectHandleCenter", center);
        }
    }

    public static void setTextSelectHandleColor(@NonNull TextView editText, @ColorInt int color) {
        Drawable drawableLeft;
        Drawable drawableRight;
        Drawable drawableCenter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawableLeft = editText.getTextSelectHandleLeft();
            drawableRight = editText.getTextSelectHandleRight();
            drawableCenter = editText.getTextSelectHandle();
        } else {
            drawableLeft = getDrawableFromTextView(editText, "mTextSelectHandleLeft", "mSelectHandleLeft");
            drawableRight = getDrawableFromTextView(editText, "mTextSelectHandleRight", "mSelectHandleRight");
            drawableCenter = getDrawableFromTextView(editText, "mTextSelectHandle", "mSelectHandleCenter");
        }
        if (drawableLeft == null)
            drawableLeft = new ColorDrawable(color);
        if (drawableRight == null)
            drawableRight = new ColorDrawable(color);
        if (drawableCenter == null)
            drawableCenter = new ColorDrawable(color);
        PorterDuffColorFilter porterDuffColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        drawableLeft.setColorFilter(porterDuffColorFilter);
        drawableRight.setColorFilter(porterDuffColorFilter);
        drawableCenter.setColorFilter(porterDuffColorFilter);
        setTextSelectHandle(editText, drawableLeft, drawableRight, drawableCenter);
    }

    public static boolean setGradientDrawableColors(@NonNull GradientDrawable drawable, @Nullable @ColorInt int[] colors, @Nullable float[] offsets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.setColors(colors, offsets);
            return true;
        } else {
            drawable.setColors(colors);
            Object mGradientState = null;
            try {
                mGradientState = GRADIENT_DRAWABLE_FIELD_GRADIENT_STATE.get(drawable);
            } catch (IllegalAccessException ignored) {
            }
            final Class<?> c_GradientState = CLASS_GRADIENT_DRAWABLE_GRADIENT_STATE;
            if (c_GradientState != null && c_GradientState.isInstance(mGradientState)) {
                try {
                    GRADIENT_DRAWABLE_GRADIENT_STATE_FIELD_POSITIONS.set(mGradientState, offsets);
                    return true;
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return false;
    }

    public static int getNextCodePointIndex(CharSequence s, int startPosition) {
        int codePoint = Character.codePointAt(s, startPosition);
        int next = startPosition + Character.charCount(codePoint);

        if (next < s.length()) {
            // skip next character if it's not helpful
            codePoint = Character.codePointAt(s, next);
            boolean skip = codePoint == 0x200D;
            skip = skip || Character.UnicodeBlock.VARIATION_SELECTORS.equals(Character.UnicodeBlock.of(codePoint));
            if (skip)
                return getNextCodePointIndex(s, next);
        }

        return next;
    }

    public static int codePointsLength(@Nullable CharSequence s) {
        final int length = s != null ? s.length() : 0;
        int n = 0;
        for (int i = 0; i < length; ) {
            int codePoint = Character.codePointAt(s, i);
            i += Character.charCount(codePoint);
            // skip this if it's ZERO WIDTH JOINER
            if (codePoint == 0x200D)
                continue;
            if (Character.UnicodeBlock.VARIATION_SELECTORS.equals(Character.UnicodeBlock.of(codePoint)))
                continue;
            ++n;
        }
        return n;
    }

    @Nullable
    public static byte[] decodeIcon(@Nullable String text, @Nullable String encoding) {
        if (text != null) {
            text = text.trim();
            int size = text.length();
            if (encoding == null || "base64".equals(encoding)) {
                byte[] base64enc = new byte[size];
                for (int i = 0; i < size; i += 1) {
                    char c = text.charAt(i);
                    base64enc[i] = (byte) (c & 0xff);
                }
                return Base64.decode(base64enc, Base64.NO_WRAP);
            }
        }
        return null;
    }

    public static String getSystemProperty(String property, String defaultValue) {
        try {
            @SuppressWarnings("rawtypes") @SuppressLint("PrivateApi")
            Class clazz = Class.forName("android.os.SystemProperties");
            @SuppressWarnings("unchecked")
            Method getter = clazz.getDeclaredMethod("get", String.class);
            String value = (String) getter.invoke(null, property);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    /**
     * @param resName
     * @param c
     * @return
     */
    public static int getResId(String resName, Class<?> c) {
        try {
            Field idField = c.getDeclaredField(resName);
            return idField.getInt(idField);
        } catch (Exception e) {
            Log.w(TAG, "getResId( " + resName + " )", e);
            return -1;
        }
    }

    public static void startAnimatable(ImageView image) {
        Drawable drawable = image.getDrawable();
        if (drawable instanceof Animatable)
            ((Animatable) drawable).start();
    }

    public static void startAnimatable(TextView textView) {
        final Runnable startAnimation = () -> {
            Drawable[] drawables = textView.getCompoundDrawables();
            for (Drawable drawable : drawables)
                if (drawable instanceof Animatable)
                    ((Animatable) drawable).start();
        };
        if (textView.isLaidOut()) {
            startAnimation.run();
        } else {
            textView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    textView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    startAnimation.run();
                }
            });
        }
    }

    /**
     * @param text            text we add the icon to
     * @param icon            drawable to use as an icon
     * @param layoutDirection will be either View.LAYOUT_DIRECTION_LTR or View.LAYOUT_DIRECTION_RTL.
     * @return SpannableString with an ImageSpan at the beginning
     */
    public static SpannableString addDrawableBeforeString(@NonNull String text, @NonNull Drawable icon, int layoutDirection) {
        final SpannableString name;
        final int pos;
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            name = new SpannableString(text + " #");
            pos = name.length() - 1;
        } else {
            name = new SpannableString("# " + text);
            pos = 0;
        }
        name.setSpan(new CenteredImageSpan(icon), pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return name;
    }

    public static SpannableString addDrawableAfterString(@NonNull String text, @NonNull Drawable icon, int layoutDirection) {
        int dir = layoutDirection != View.LAYOUT_DIRECTION_RTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        return addDrawableBeforeString(text, icon, dir);
    }

    public static String appendString(@NonNull String textA, @Nullable String glue, @NonNull String textB, int layoutDirection) {
        int expectedLength = textA.length() + textB.length();
        if (glue != null)
            expectedLength += glue.length();
        StringBuilder builder = new StringBuilder(expectedLength);
        builder.append(layoutDirection == View.LAYOUT_DIRECTION_RTL ? textB : textA);
        if (glue != null)
            builder.append(glue);
        builder.append(layoutDirection == View.LAYOUT_DIRECTION_RTL ? textA : textB);
        return builder.toString();
    }

    @Nullable
    public static View inflateViewStub(@Nullable View view) {
        if (view instanceof ViewStubPreview) {
            // ViewStubPreview already calls updateConstraintsAfterStubInflate
            return ((ViewStubPreview) view).inflate();
        }

        if (!(view instanceof ViewStub))
            return view;

        ViewStub stub = (ViewStub) view;
        int stubId = stub.getId();

        // get parent before the call to inflate
        ConstraintLayout constraintLayout = stub.getParent() instanceof ConstraintLayout ? (ConstraintLayout) stub.getParent() : null;

        View inflatedView = stub.inflate();
        int inflatedId = inflatedView.getId();

        updateConstraintsAfterStubInflate(constraintLayout, stubId, inflatedId);

        return inflatedView;
    }

    public static void updateConstraintsAfterStubInflate(@Nullable ConstraintLayout constraintLayout, int stubId, int inflatedId) {
        if (inflatedId == View.NO_ID)
            return;
        // change parent ConstraintLayout constraints
        if (constraintLayout != null && stubId != inflatedId) {
            int childCount = constraintLayout.getChildCount();
            for (int childIdx = 0; childIdx < childCount; childIdx += 1) {
                View child = constraintLayout.getChildAt(childIdx);
                if (child instanceof ConstraintHelper) {
                    // get a copy of the id list
                    int[] refIds = ((ConstraintHelper) child).getReferencedIds();
                    boolean changed = false;
                    // change constraint reference IDs
                    for (int idx = 0; idx < refIds.length; idx += 1) {
                        if (refIds[idx] == stubId) {
                            refIds[idx] = inflatedId;
                            changed = true;
                        }
                    }
                    if (changed)
                        ((ConstraintHelper) child).setReferencedIds(refIds);
                }
                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) child.getLayoutParams();
                if (changeConstraintLayoutParamsTarget(params, stubId, inflatedId))
                    child.setLayoutParams(params);
            }
        }
    }

    private static boolean changeConstraintLayoutParamsTarget(ConstraintLayout.LayoutParams params, int fromId, int toId) {
        boolean changed = false;
        if (params.leftToLeft == fromId) {
            params.leftToLeft = toId;
            changed = true;
        }
        if (params.leftToRight == fromId) {
            params.leftToRight = toId;
            changed = true;
        }
        if (params.rightToLeft == fromId) {
            params.rightToLeft = toId;
            changed = true;
        }
        if (params.rightToRight == fromId) {
            params.rightToRight = toId;
            changed = true;
        }
        if (params.topToTop == fromId) {
            params.topToTop = toId;
            changed = true;
        }
        if (params.topToBottom == fromId) {
            params.topToBottom = toId;
            changed = true;
        }
        if (params.bottomToTop == fromId) {
            params.bottomToTop = toId;
            changed = true;
        }
        if (params.bottomToBottom == fromId) {
            params.bottomToBottom = toId;
            changed = true;
        }
        if (params.baselineToBaseline == fromId) {
            params.baselineToBaseline = toId;
            changed = true;
        }
        if (params.baselineToTop == fromId) {
            params.baselineToTop = toId;
            changed = true;
        }
        if (params.circleConstraint == fromId) {
            params.circleConstraint = toId;
            changed = true;
        }
        if (params.startToEnd == fromId) {
            params.startToEnd = toId;
            changed = true;
        }
        if (params.startToStart == fromId) {
            params.startToStart = toId;
            changed = true;
        }
        if (params.endToStart == fromId) {
            params.endToStart = toId;
            changed = true;
        }
        if (params.endToEnd == fromId) {
            params.endToEnd = toId;
            changed = true;
        }
        return changed;
    }

    public interface GetDrawable {
        @Nullable
        Drawable getDrawable(@NonNull Context context);
    }

    public interface SetDrawable {
        void setDrawable(@NonNull View view, @NonNull Drawable drawable);
    }

    public static abstract class AsyncViewSet extends AsyncTask<Void, Drawable> {
        protected final WeakReference<View> weakView;

        protected AsyncViewSet(View view) {
            super();
            this.weakView = new WeakReference<>(view);
            if (view.getTag() instanceof AsyncViewSet)
                ((AsyncViewSet) view.getTag()).cancel(true);
            view.setTag(this);
        }

        @Override
        protected Drawable doInBackground(Void param) {
            View image = weakView.get();
            Activity act = Utilities.getActivity(image);
            if (isCancelled() || act == null || image.getTag() != this) {
                weakView.clear();
                return null;
            }

            Context ctx = image.getContext();
            return getDrawable(ctx);
        }

        @WorkerThread
        protected abstract Drawable getDrawable(Context context);

        @UiThread
        protected abstract void setDrawable(@NonNull View view, @NonNull Drawable drawable);

        @Override
        protected void onPostExecute(Drawable drawable) {
            View view = weakView.get();
            if (view == null || view.getTag() != this)
                return;
            Activity act = Utilities.getActivity(view);
            if (act == null || drawable == null) {
                weakView.clear();
                return;
            }
            setDrawable(view, drawable);
            view.setTag(null);
        }

        public void execute() {
            TaskRunner.executeOnExecutor(ResultViewHelper.EXECUTOR_LOAD_ICON, this);
        }
    }

    public static abstract class AsyncSetDrawable extends AsyncViewSet {
        protected AsyncSetDrawable(@NonNull ImageView image) {
            super(image);
            image.setImageResource(android.R.color.transparent);
        }

        @Override
        protected void setDrawable(@NonNull View image, @NonNull Drawable drawable) {
            ((ImageView) image).setImageDrawable(drawable);
        }
    }
}
