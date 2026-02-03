# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===========================
# vFlow 保留规则
# ===========================

# 1. 保留所有数据模型 (Data Classes)
-keep class com.chaomixian.vflow.core.workflow.model.** { *; }
-keep class com.chaomixian.vflow.core.module.InputDefinition { *; }
-keep class com.chaomixian.vflow.core.module.OutputDefinition { *; }
-keep class com.chaomixian.vflow.core.module.ActionMetadata { *; }
-keep class com.chaomixian.vflow.core.module.BlockBehavior { *; }

# 2. 保留所有模块实现类 (Modules)
-keep class com.chaomixian.vflow.core.workflow.module.** { *; }
-keep class com.chaomixian.vflow.core.module.** { *; }

# 3. 保留触发器处理器 (Trigger Handlers)
-keep class com.chaomixian.vflow.core.workflow.module.triggers.handlers.** { *; }
-keep interface com.chaomixian.vflow.core.workflow.module.triggers.handlers.ITriggerHandler { *; }

# 4. 保留自定义变量类型 (VObject 及子类)
-keep class com.chaomixian.vflow.core.types.** { *; }
-keep class com.chaomixian.vflow.core.module.*Variable { *; }
-keep class com.chaomixian.vflow.core.workflow.module.notification.NotificationObject { *; }

# 5. 保留 Gson 相关
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# 6. 保留 Parcelable 实现 (用于 Intent 传递)
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# 7. 保留 Lua 相关
-keep class org.luaj.vm2.** { *; }

# 8. 忽略 XR/javax 警告
-dontwarn javax.script.**
-dontwarn org.luaj.vm2.script.**
-dontwarn com.android.extensions.xr.**
-dontwarn com.google.androidxr.**
-dontwarn org.apache.bcel.classfile.**
-dontwarn org.apache.bcel.generic.**

# 9. 保留数据仓库模型 (Repository & Update)
-keep class com.chaomixian.vflow.data.repository.model.** { *; }
-keep class com.chaomixian.vflow.data.update.** { *; }
-keep class com.chaomixian.vflow.data.repository.api.** { *; }

# 10. 保留 Shizuku & AIDL 相关
-keep class com.chaomixian.vflow.services.IShizukuUserService { *; }
-keep class com.chaomixian.vflow.services.IShizukuUserService$Stub { *; }
-keep class com.chaomixian.vflow.services.ShizukuUserService { *; }
# 保留 Shizuku 官方库的 provider
-keep class dev.rikka.shizuku.** { *; }

# 11. 保留 ML Kit (文字识别)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.tasks.** { *; }

# 12. 保留 Kotlin Coroutines (协程) 内部组件
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# 13. 保留 Coil (图片加载)
-keep class coil.** { *; }

# 14. 保留 OkHttp 相关
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 15. 保留基本属性和调试信息
# LineNumberTable: 确保崩溃日志（LogManager）能显示行号，方便排查问题
# SourceFile: 保留源文件名
# EnclosingMethod, InnerClasses: 防止内部类/匿名类在反射时找不到宿主
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod,InnerClasses,MemberClasses

# 16. 保留 Kotlin 元数据
-keep class kotlin.Metadata { *; }
-keepclassmembers class com.chaomixian.vflow.** {
    @kotlin.Metadata *;
}

# 17. 保留注解属性
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# 18. 通用枚举保护
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 19. Android 组件与 View 的构造函数
-keepclassmembers class * extends android.view.View {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
}
# 确保 Fragment 的无参构造函数存在（系统恢复 Fragment 时需要）
-keepclassmembers class * extends androidx.fragment.app.Fragment {
   public <init>();
}

# 20. 针对 Material Design 组件的反射
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# 21. 针对 ViewBinding
-keep class androidx.databinding.** { *; }
-keepclassmembers class * extends androidx.viewbinding.ViewBinding {
    public static ** bind(android.view.View);
    public static ** inflate(android.view.LayoutInflater);
    public static ** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}

# 22. 确保自定义控件不被混淆
-keep class com.chaomixian.vflow.ui.workflow_editor.RichTextView { *; }
-keep class com.chaomixian.vflow.ui.overlay.** { *; }

# 23. 确保 JS/Lua 脚本引擎的底层兼容
-dontwarn java.awt.**
-dontwarn java.beans.**

# 24. 确保 Shizuku 的 Binder 接口方法不被改名
-keepclassmembers class * extends android.os.Binder {
    <methods>;
}

# 25. OpenCV native library
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** { *; }
-dontwarn org.opencv.**