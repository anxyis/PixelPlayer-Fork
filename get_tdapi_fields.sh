#!/bin/bash
unzip -q -c app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes/org/drinkless/tdlib/TdApi.class > /dev/null 2>&1
if [ $? -ne 0 ]; then
  javap -cp $(find . -name "*.jar" | grep tdlib | head -n 1) org.drinkless.tdlib.TdApi\$Message
  javap -cp $(find . -name "*.jar" | grep tdlib | head -n 1) org.drinkless.tdlib.TdApi\$MessageContent
fi
