# Auto Builder (Fabric, Minecraft 1.21.1)
Build:   gradle wrapper --gradle-version 8.8  &&  ./gradlew build     (JDK 21)
Output:  build/libs/autobuilder-1.0.0.jar  -> drop into .minecraft/mods with Fabric API 0.102.0+1.21.1
Use:     put .litematic files in .minecraft/schematics, run  /autobuilder load <file>, stand where the origin should be,
         RIGHT SHIFT = menu, KP Enter = start/stop, KP 0 = pause, KP . = undo 5, KP + = skip layer.
Config:  .minecraft/config/autobuilder.json

GitHub build (no local setup): push this folder to a GitHub repo, open the Actions tab, run "build",
then download the "autobuilder-jar" artifact from the finished run.
