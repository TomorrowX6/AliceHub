#include <SDL.h>
#include <string>
#ifdef __ANDROID__
#include <cstdlib>
#endif

#ifdef ALICEHUB_IOS
int system3_entry(int argc, char **argv);

extern "C" int system3_main(int argc, char **argv)
{
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
    return system3_entry(argc, argv);
}
#endif

SDL_RWops *open_resource(const char *name, const char *type)
{
    std::string path;
#ifndef __ANDROID__
    char *base = SDL_GetBasePath();
    if (!base)
        return nullptr;
    path = base;
    SDL_free(base);
#endif
#ifdef ALICEHUB_IOS
    if (std::string(type) == "fonts")
        return SDL_RWFromFile((path + name).c_str(), "rb");
    path += "system3/";
#endif
    return SDL_RWFromFile((path + type + "/" + name).c_str(), "rb");
}

SDL_RWops *open_file(const char *name)
{
#ifdef __ANDROID__
    // SDL loads relative paths from APK assets; game data is on disk.
    char *path = realpath(name, nullptr);
    if (!path)
        return nullptr;
    SDL_RWops *file = SDL_RWFromFile(path, "rb");
    free(path);
    return file;
#else
    return SDL_RWFromFile(name, "rb");
#endif
}
