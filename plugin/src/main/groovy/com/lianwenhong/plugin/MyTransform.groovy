package com.lianwenhong.plugin

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.gradle.api.Project

class MyTransform extends Transform {

    def project
    // 缓存字节码对象CtClass的容器
    def pool = ClassPool.default

    MyTransform(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return "hot_fix_inject"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) {
        super.transform(transformInvocation)
        println "========开始处理字节码文件========"

        // 向缓存中加入android.jar，不然找不到android相关的所有类
        project.android.bootClasspath.each {
            pool.appendClassPath(it.absolutePath)
            println " >>> 系统类库(project.android.bootClasspath):" + it.absolutePath
        }

        // 遍历项目中的所有输入文件
        transformInvocation.inputs.each {
            // 遍历jar文件
            it.jarInputs.each {
                // 将该路径下的所有class都加入缓存中
                pool.insertClassPath(it.file.absolutePath)
                // 获取jar文件的输出目录
                def dest = transformInvocation.outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.JAR)
                // jar文件中的class不处理，直接拷贝到下一个transform中
                FileUtils.copyFile(it.file, dest)
            }
            // 遍历项目中所有的输入目录
            it.directoryInputs.each {
                def preFileName = it.file.absolutePath
                // 将该路径下的所有class都加入缓存中
                pool.insertClassPath(preFileName)
                // 将某个包加入缓存，例如这里单独将android.os.Bundle包加入缓存（只是为了演示，因为这个包已经在android.jar中了，所以并不需要单独加入）
                // pool.importPackage("android.os.Bundle")
                println " >>> 输入文件夹:" + preFileName
                findTarget(it.file, preFileName)
                // 获取文件夹的输出目录
                def dest = transformInvocation.outputProvider.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                // 拷贝文件夹给下一个Transform任务
                FileUtils.copyDirectory(it.file, dest)
            }
        }
    }

    /**
     * 遍历并修改文件夹下的所有class文件
     * @param file
     */
    void findTarget(File file, String filePath) {
        if (file.isDirectory()) {
            file.listFiles().each {
                findTarget(it, filePath)
            }
        } else {
            modify(file, filePath)
        }
    }

    /**
     * 动态修改class文件
     * @param file
     * @param filePath
     */
    void modify(File file, String filePath) {

        def fileName = file.absolutePath
        if (!fileName.endsWith(SdkConstants.DOT_CLASS)) {
            return
        }
        if (fileName.contains('R$') || fileName.contains('R.class') || fileName.contains('BuildConfig.class')) {
            return
        }
        if (fileName.contains('PatchProxy.class')) {
            return
        }
        println " >>> filePath:" + filePath
        println " >>> fileName:" + fileName
        // 获得全类名 key -> 字节码 ctClass(字节码文件在内存中的对象表现) -> 修改
        // 从/Users/lianwenhong/AndroidStudioProjects/demos/JavassistDemo/app/build/intermediates/javac/release/classes/com/lianwenhong/javassistdemo/MainActivity.class
        // 中获取
        // com.lianwenhong.javassistdemo.MainActivity.class全类名
        def clzName = fileName.replace(filePath, "").replace(File.separator, ".")
        def name = clzName.replace(SdkConstants.DOT_CLASS, "").substring(1)
        println " >>> className:" + name

        CtClass ctClass = pool.get(name)
//        if (name.contains('com.lianwenhong.javassistdemo')) {
        def body = 'if(!com.lianwenhong.javassistdemo.PatchProxy.isSupport()){android.util.Log.e("lianwenhong", " >>> 无补丁包，不需要动态修改 <<< ");}'
        addCode(ctClass, body, filePath)
//        }
    }

    /**
     * 过滤掉不相关的class文件
     * @param file
     * @param filePath
     * @return
     */
    String filterClz(File file, String filePath) {
        def fileName = file.absolutePath
        if (!fileName.endsWith(SdkConstants.DOT_CLASS)) {
            return
        }
        if (fileName.contains('R$') || fileName.contains('R.class') || fileName.contains('BuildConfig.class')) {
            return
        }
        println " >>> filePath:" + filePath
        println " >>> fileName:" + fileName
        // 获得全类名 key -> 字节码 ctClass(字节码文件在内存中的对象表现) -> 修改
        // 从/Users/lianwenhong/AndroidStudioProjects/demos/JavassistDemo/app/build/intermediates/javac/release/classes/com/lianwenhong/javassistdemo/MainActivity.class
        // 中获取
        // com.lianwenhong.javassistdemo.MainActivity.class全类名
        def clzName = fileName.replace(filePath, "").replace(File.separator, ".").replace(SdkConstants.DOT_CLASS, "").substring(1)
        println " >>> className:" + clzName

        return clzName
    }

/**
 * 注入动态添加的代码
 * @param ctClass
 * @param body
 * @param fileName
 */
    void addCode(CtClass ctClass, String body, String fileName) {
        CtMethod[] methods = ctClass.getDeclaredMethods()
        methods.each {
            it.insertAfter(body)
        }
        ctClass.writeFile(fileName)
        ctClass.detach()
    }

}
