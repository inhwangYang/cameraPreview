LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#opencv
OPENCVROOT:= c:\OpenCV-android-sdk
OPENCV_CAMERA_MODULES:=on
OPENCV_INSTALL_MODULES:=on
OPENCV_LIB_TYPE:=SHARED

include ${OPENCVROOT}\sdk\native\jni\OpenCV.mk

LOCAL_SRC_FILES := main.c
LOCAL_LDLIBS += -llog -ljnigraphics -lz -lm
LOCAL_MODULE := effect


include $(BUILD_SHARED_LIBRARY)
include $(CLEAR_VARS)
