#pragma once

#include <SDL.h>
#include <switch.h>

static inline SDL_GameControllerButton alicehub_switch_face_button(SDL_JoystickID joystick, SDL_GameControllerButton button)
{
    if ((button != SDL_CONTROLLER_BUTTON_A && button != SDL_CONTROLLER_BUTTON_B) ||
        joystick < HidNpadIdType_No1 || joystick > HidNpadIdType_No8)
        return button;

    // Match devkitPro SDL's sideways Joy-Con test for this joystick slot.
    HidNpadIdType id = (HidNpadIdType)joystick;
    if (!(hidGetNpadStyleSet(id) & HidNpadStyleTag_NpadJoyDual) &&
        (hidGetNpadDeviceType(id) & (HidDeviceTypeBits_JoyLeft | HidDeviceTypeBits_JoyRight)))
        return button;

    return button == SDL_CONTROLLER_BUTTON_A ? SDL_CONTROLLER_BUTTON_B : SDL_CONTROLLER_BUTTON_A;
}
