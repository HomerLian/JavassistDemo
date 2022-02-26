package com.lianwenhong.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class MyPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println "开始修改字节码"
        project.android.registerTransform(new MyTransform(project))
    }

}