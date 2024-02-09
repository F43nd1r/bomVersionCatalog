tasks {
    register("check") {
        group = "verification"
        gradle.includedBuilds.forEach {
            dependsOn(it.task(":check"))
        }
    }
}