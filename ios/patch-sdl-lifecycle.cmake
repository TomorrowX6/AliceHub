set(path "${SOURCE_DIR}/src/video/uikit/SDL_uikitevents.m")
file(READ "${path}" source)
set(old [=[    /* See the comment in the function definition. */
#if defined(SDL_VIDEO_OPENGL_ES) || defined(SDL_VIDEO_OPENGL_ES2)
    UIKit_GL_RestoreCurrentContext();]=])
set(new [=[    /* AliceHub: do not return to engine rendering while UIKit is inactive. */
    while (UIApplication.sharedApplication.applicationState != UIApplicationStateActive) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.05, TRUE);
    }

    /* See the comment in the function definition. */
#if defined(SDL_VIDEO_OPENGL_ES) || defined(SDL_VIDEO_OPENGL_ES2)
    UIKit_GL_RestoreCurrentContext();]=])
string(FIND "${source}" "${old}" position)
string(FIND "${source}" "${new}" patched)
if(NOT patched EQUAL -1)
  return()
elseif(position EQUAL -1)
  message(FATAL_ERROR "Unexpected SDL UIKit event pump source")
endif()
string(REPLACE "${old}" "${new}" source "${source}")
file(WRITE "${path}" "${source}")
