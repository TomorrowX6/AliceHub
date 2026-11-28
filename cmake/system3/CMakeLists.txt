cmake_minimum_required(VERSION 3.18)
project(AliceHubSystem3 LANGUAGES C CXX)

if(POLICY CMP0135)
  cmake_policy(SET CMP0135 NEW)
endif()

include(FetchContent)
FetchContent_Declare(system3_source
  URL https://codeload.github.com/kichikuou/system3-sdl2/tar.gz/ca0f09a19e149f1906e0dd423cf6e3eb4ff06efe
  URL_HASH SHA256=bd1944c7703ca7fe9086b0492186961e8449b97f4f1eb021cab42b3fbaf81626
  SOURCE_SUBDIR unused) # v1.8.0; build against AliceHub's SDL, not the standalone app
FetchContent_Declare(ymfm
  URL https://codeload.github.com/aaronsgiles/ymfm/tar.gz/10c72f79bea3e0ab66af32d4295519aa17e6ea0f
  URL_HASH SHA256=f361fe98f7d420e9f5cde8f78687f97215729bd53776e020013c2808ebc4e955
  SOURCE_SUBDIR unused)
FetchContent_Declare(sdl2_ttf
  URL https://github.com/libsdl-org/SDL_ttf/releases/download/release-2.24.0/SDL2_ttf-2.24.0.tar.gz
  URL_HASH SHA256=0b2bf1e7b6568adbdbc9bb924643f79d9dedafe061fa1ed687d1d9ac4e453bfd
  SOURCE_SUBDIR unused)
FetchContent_MakeAvailable(system3_source ymfm sdl2_ttf)

find_package(SDL2 CONFIG REQUIRED)
find_package(Freetype REQUIRED)

set(src "${system3_source_SOURCE_DIR}/src")
file(GLOB vm_sources "${src}/sys/*.cpp")
list(REMOVE_ITEM vm_sources
  "${src}/sys/mako_midi.cpp"
  "${src}/sys/scenario.cpp"
  "${src}/sys/msgskip.cpp"
  "${src}/sys/nact_sys1.cpp"
  "${src}/sys/nact_gakuenking.cpp"
  "${src}/sys/nact.cpp"
  "${src}/sys/nact_sys2.cpp"
  "${src}/sys/nact_sys3.cpp"
  "${src}/sys/ags.cpp"
  "${src}/sys/ags_text.cpp"
  "${src}/sys/nact_input.cpp")

function(alicehub_patch_replace var orig repl desc)
  set(_before "${${var}}")
  string(REPLACE "${orig}" "${repl}" _after "${_before}")
  if(_after STREQUAL _before)
    message(FATAL_ERROR "Could not apply System 3 patch: ${desc}")
  endif()
  set(${var} "${_after}" PARENT_SCOPE)
endfunction()

# --- Patch fm/makofm.h, fm/makofm.cpp, and copy fm/mako_ymfm.{h,cpp} so #include "makofm.h" picks patched header ---
file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/fm")
set(orig_makofm_h "${src}/fm/makofm.h")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_makofm_h}")
file(READ "${orig_makofm_h}" patched_makofm_h)
alicehub_patch_replace(patched_makofm_h
  [=[	uint16_t GetWord(int offset) const {
		return data[offset] | data[offset + 1] << 8;
	}]=]
  [=[	uint16_t GetWord(size_t offset) const {
		if (offset >= data.size() || data.size() - offset < 2)
			return 0;
		return data[offset] | data[offset + 1] << 8;
	}]=]
  "MakoFM::GetWord bounds check")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/fm/makofm.h" CONTENT "${patched_makofm_h}" @ONLY)

foreach(_ymfm_file mako_ymfm.h mako_ymfm.cpp)
  set(_ymfm_src "${src}/fm/${_ymfm_file}")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_ymfm_src}")
  file(READ "${_ymfm_src}" _ymfm_content)
  file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/fm/${_ymfm_file}" CONTENT "${_ymfm_content}" @ONLY)
endforeach()

set(orig_makofm_cpp "${src}/fm/makofm.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_makofm_cpp}")
file(READ "${orig_makofm_cpp}" patched_makofm_cpp)
alicehub_patch_replace(patched_makofm_cpp
  [=[MakoFM::MakoFM(std::vector<uint8_t> data) :
	data(std::move(data)),
	tone_offset(GetWord(0)),
	ver(GetWord(2)) {
	memset(work, 0, sizeof(work));
	for (int ch = 0; ch < MAKO_MAXCH; ch++) {
		work[ch].Q = 7;
		work[ch].default_length = 64;
		work[ch].octave = 4;
		work[ch].octave_temp = 4;
		work[ch].vol = 120;
		work[ch].panpot = 0x40;
	}
}]=]
  [=[MakoFM::MakoFM(std::vector<uint8_t> data) :
	data(std::move(data)),
	tone_offset(GetWord(0)),
	ver(GetWord(2)) {
	memset(work, 0, sizeof(work));
	bool valid_header = this->data.size() >= static_cast<size_t>(4 + MAKO_MAXCH * 4) && !(tone_offset == 0 && ver == 0);
	for (int ch = 0; ch < MAKO_MAXCH; ch++) {
		work[ch].Q = 7;
		work[ch].default_length = 64;
		work[ch].octave = 4;
		work[ch].octave_temp = 4;
		work[ch].vol = 120;
		work[ch].panpot = 0x40;
		if (!valid_header) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
		}
	}
}]=]
  "MakoFM header validation in ctor")

alicehub_patch_replace(patched_makofm_cpp
  [=[void MakoFM::InitChannel(int ch) {
	work[ch].part_offset = GetWord(4 + ch * 4);
	if (!work[ch].part_offset) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	uint16_t block0_offset = GetWord(work[ch].part_offset);
	if (block0_offset >= 0xff00) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	work[ch].jump_ptr = work[ch].part_offset + 2;
	work[ch].seq_ptr = block0_offset;
}]=]
  [=[void MakoFM::InitChannel(int ch) {
	if (work[ch].part_enable == PART_DISABLED ||
	    data.size() < static_cast<size_t>(4 + MAKO_MAXCH * 4) ||
	    (tone_offset == 0 && ver == 0)) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	work[ch].part_offset = GetWord(4 + ch * 4);
	if (!work[ch].part_offset || static_cast<size_t>(work[ch].part_offset) + 2 > data.size()) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	uint16_t block0_offset = GetWord(work[ch].part_offset);
	if (block0_offset >= 0xff00 || block0_offset >= data.size()) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	work[ch].jump_ptr = work[ch].part_offset + 2;
	work[ch].seq_ptr = block0_offset;
}]=]
  "MakoFM::InitChannel bounds check")

alicehub_patch_replace(patched_makofm_cpp
  [=[void MakoFM::ExecSequence(int ch, bool execcmd) {
	if (execcmd)
		goto execcmd;

	if (work[ch].part_enable != 1)
		return;
	if (work[ch].length_counter > 1) {
		work[ch].length_counter--;
		return;
	}
	if (!work[ch].rest)
		goto note1;

 execcmd:
	cmdFF = 0;
	work[ch].seq_ptr = ExecCmd(ch, &data[work[ch].seq_ptr]) - data.data();
	if (cmdFF) {
		uint16_t block_addr = GetWord(work[ch].jump_ptr);
		work[ch].jump_ptr += 2;
		if (block_addr >> 8 == 0xff) {
			part_looped |= 1 << ch;
			if (work[ch].part_enable == PART_DISABLED)
				return;
			if (work[ch].part_enable == 0) {
				work[ch].part_enable = 0xff;
				cmdFF = 0;
				return;
			}
			work[ch].jump_ptr = work[ch].part_offset + (block_addr & 0xff) * 2;
			block_addr = GetWord(work[ch].jump_ptr);
			work[ch].jump_ptr += 2;
		}
		work[ch].seq_ptr = block_addr;
		cmdFF = 0;
		goto execcmd;
	}]=]
  [=[void MakoFM::ExecSequence(int ch, bool execcmd) {
	if (work[ch].part_enable == PART_DISABLED || work[ch].seq_ptr >= data.size()) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	int steps = 0;
	if (execcmd)
		goto execcmd;

	if (work[ch].part_enable != 1)
		return;
	if (work[ch].length_counter > 1) {
		work[ch].length_counter--;
		return;
	}
	if (!work[ch].rest)
		goto note1;

 execcmd:
	if (work[ch].part_enable == PART_DISABLED || work[ch].seq_ptr >= data.size() || ++steps > 1024) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return;
	}
	cmdFF = 0;
	{
		const uint8_t* next_code = ExecCmd(ch, &data[work[ch].seq_ptr]);
		if (work[ch].part_enable == PART_DISABLED)
			return;
		if (next_code < data.data() || next_code > data.data() + data.size()) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			return;
		}
		work[ch].seq_ptr = static_cast<uint16_t>(next_code - data.data());
	}
	if (cmdFF) {
		if (static_cast<size_t>(work[ch].jump_ptr) + 2 > data.size()) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			return;
		}
		uint16_t block_addr = GetWord(work[ch].jump_ptr);
		work[ch].jump_ptr += 2;
		if (block_addr >> 8 == 0xff) {
			part_looped |= 1 << ch;
			if (work[ch].part_enable == PART_DISABLED)
				return;
			if (work[ch].part_enable == 0) {
				work[ch].part_enable = 0xff;
				cmdFF = 0;
				return;
			}
			size_t next_jump = static_cast<size_t>(work[ch].part_offset) + (block_addr & 0xff) * 2;
			if (next_jump + 2 > data.size()) {
				work[ch].part_enable = PART_DISABLED;
				part_looped |= 1 << ch;
				return;
			}
			work[ch].jump_ptr = static_cast<uint16_t>(next_jump);
			block_addr = GetWord(work[ch].jump_ptr);
			work[ch].jump_ptr += 2;
		}
		if (block_addr >= data.size()) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			return;
		}
		work[ch].seq_ptr = block_addr;
		cmdFF = 0;
		goto execcmd;
	}]=]
  "MakoFM::ExecSequence bounds & disable guards")

alicehub_patch_replace(patched_makofm_cpp
  [=[const uint8_t* MakoFM::ExecCmd(int ch, const uint8_t* code) {
	bool cont = true;
	while (cont) {
		if (*code < 0xe0)
			return ExecNote(ch, code);
		cont = false;
		uint8_t a = *code++;
		switch (a) {
		case 0xec:	// Part end
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			break;

		case 0xff:	// Jump
			cmdFF = 0xff;
			break;

		case 0xeb:	// Pan
			work[ch].panpot = *code++;
			ExecPan(ch);
			cont = true;
			break;

		case 0xee:  // Octave++
			work[ch].octave++;
			cont = true;
			break;

		case 0xef:  // Octave--
			work[ch].octave--;
			cont = true;
			break;

		case 0xf0:  // Octave
			work[ch].octave = *code++;
			cont = true;
			break;

		case 0xf1:  // Temporary octave
			work[ch].octave_temp = *code++;
			cont = true;
			break;

		case 0xf2:  // Q (1-8)
			work[ch].flags &= ~ALT_Q_FORMAT;
			work[ch].Q = *code++ + 1;
			cont = true;
			break;

		case 0xea:  // Q (alt)
			work[ch].flags |= ALT_Q_FORMAT;
			work[ch].Q = *code++;
			cont = true;
			break;

		case 0xe9:  // Tie
			work[ch].flags |= SKIP_KEYOFF;
			cont = true;
			break;

		case 0xe7:  // Frq LFO
			work[ch].flfo_params.delay   = code[0] | code[1] << 8;
			work[ch].flfo_params.counter = code[2] | code[3] << 8;
			work[ch].flfo_params.value   = code[4] | code[5] << 8;
			work[ch].flfo_params.max     = code[6] | code[7] << 8;
			code += 8;
			work[ch].flags |= SW_FRQLFO_ENABLED;
			work[ch].flags &= ~FRQLFO_DIRECTION;
			SetFLFO(ch, false);
			cont = true;
			break;

		case 0xe6:  // Vol LFO (PSG)
			work[ch].vlfo_params.delay   = code[0] | code[1] << 8;
			work[ch].vlfo_params.counter = code[2] | code[3] << 8;
			work[ch].vlfo_params.value   = code[4] | code[5] << 8;
			work[ch].vlfo_params.max     = code[6] | code[7] << 8;
			code += 8;
			work[ch].fm_delay = work[ch].vlfo_params.delay;
			work[ch].sw_lfo_val = 0;
			work[ch].flags |= SW_VOLLFO_ENABLED;
			work[ch].flags &= ~VOLLFO_DIRECTION;
			SetVLFO(ch, false);
			cont = true;
			break;

		case 0xf3:  // Default length
			{
				uint8_t a = *code++;
				if (a < 0x80)
					work[ch].default_length = a;
				else
					work[ch].default_length = (a - 0x80) << 8 | *code++;
			}
			cont = true;
			break;

		case 0xe5:  // LFO flags
			work[ch].flags &= ~0x70;
			work[ch].flags |= (*code++ & 7) << 4;
			cont = true;
			break;

		case 0xe8:  // Vol LFO (FM)
			work[ch].vlfo_params.delay   = code[0] | code[1] << 8;
			work[ch].vlfo_params.counter = code[2] | code[3] << 8;
			work[ch].vlfo_params.value   = code[4] | code[5] << 8;
			work[ch].vlfo_params.max     = code[6] | code[7] << 8;
			work[ch].fm_delay            = code[8] | code[9] << 8;
			code += 10;
			work[ch].flags |= SW_VOLLFO_ENABLED;
			cont = true;
			break;

		case 0xf4:  // Tempo
			tempo = *code++;
			cont = true;
			break;

		case 0xf5:  // FM tone set
			work[ch].tone_num = *code++;
			SetFMTone(ch, &data[tone_offset + TONE_DATA_SIZE * work[ch].tone_num]);
			ExecHWLFO(ch);
			cont = true;
			break;

		case 0xf7:  // Temp octave--
			work[ch].octave_temp = work[ch].octave - 1;
			work[ch].use_temp_octave = 1;
			cont = true;
			break;

		case 0xf8:  // Temp octave++
			work[ch].octave_temp = work[ch].octave + 1;
			work[ch].use_temp_octave = 1;
			cont = true;
			break;

		case 0xf9:  // Absolute vol
			work[ch].vol = work[ch].tl4_vol = std::min(*code++, static_cast<uint8_t>(127));
			SetVol(ch);
			cont = true;
			break;

		case 0xe1:  // Relative vol
			work[ch].vol = work[ch].tl4_vol =
				std::min(static_cast<uint8_t>(work[ch].vol + *code++),
						 static_cast<uint8_t>(127));
			SetVol(ch);
			cont = true;
			break;

		case 0xe4:  // HW_LFO
			hwlfo = *code++ | 8;
			work[ch].pms_param = *code++;
			work[ch].ams_param = *code++;
			work[ch].flags |= USE_HW_LFO;
			ExecHWLFO(ch);
			cont = true;
			break;

		case 0xfc:  // Detune
			work[ch].detune = *(const int8_t*)code++;
			cont = true;
			break;

		case 0xfe:
			code += 3;
			cont = true;
			break;

		case 0xf6:
			code += 2;
			cont = true;
			break;

		case 0xfa:
		case 0xfd:
			code++;
			cont = true;
			break;

		case 0xe0:
			current_mark = *code++;
			cont = true;
			break;

		default:
			WARNING("MakoFM: unknown command 0x%02x", a);
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			break;
		}
	}
	return code;
}

const uint8_t* MakoFM::ExecNote(int ch, const uint8_t* code) {
	uint8_t note = *code++;
	if (note < 13) {
		work[ch].note = note;
		work[ch].length = *code++;
	} else if (note < 26) {
		work[ch].note = note - 13;
		work[ch].length = code[0] | code[1] << 8;
		code += 2;
	} else if (0x80 <= note && note < 0x80 + 13) {
		work[ch].note = note - 0x80;
		work[ch].length = work[ch].default_length;
	} else {
		WARNING("MakoFM: unknown command: 0x%02x", note);
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return code;
	}

	if (!work[ch].use_temp_octave)
		work[ch].octave_temp = work[ch].octave;
	work[ch].use_temp_octave = 0;

	uint16_t freq = 0;
	if (work[ch].note) {
		if (IsFMchannel(ch))
			freq = FMFreqTable[work[ch].note - 1] | work[ch].octave_temp << 11;
		else
			freq = PSGFreqTable[work[ch].note - 1] >> work[ch].octave_temp;
	}]=]
  [=[const uint8_t* MakoFM::ExecCmd(int ch, const uint8_t* code) {
	const uint8_t* const end = data.data() + data.size();
	auto need = [&](size_t n) -> bool {
		if (code < data.data() || code > end || static_cast<size_t>(end - code) < n) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			return false;
		}
		return true;
	};

	bool cont = true;
	while (cont) {
		if (!need(1))
			break;
		if (*code < 0xe0)
			return ExecNote(ch, code);
		cont = false;
		uint8_t a = *code++;
		switch (a) {
		case 0xec:	// Part end
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			break;

		case 0xff:	// Jump
			cmdFF = 0xff;
			break;

		case 0xeb:	// Pan
			if (!need(1)) break;
			work[ch].panpot = *code++;
			ExecPan(ch);
			cont = true;
			break;

		case 0xee:  // Octave++
			work[ch].octave++;
			cont = true;
			break;

		case 0xef:  // Octave--
			work[ch].octave--;
			cont = true;
			break;

		case 0xf0:  // Octave
			if (!need(1)) break;
			work[ch].octave = *code++;
			cont = true;
			break;

		case 0xf1:  // Temporary octave
			if (!need(1)) break;
			work[ch].octave_temp = *code++;
			cont = true;
			break;

		case 0xf2:  // Q (1-8)
			if (!need(1)) break;
			work[ch].flags &= ~ALT_Q_FORMAT;
			work[ch].Q = (*code++ & 7) + 1;
			cont = true;
			break;

		case 0xea:  // Q (alt)
			if (!need(1)) break;
			work[ch].flags |= ALT_Q_FORMAT;
			work[ch].Q = *code++;
			cont = true;
			break;

		case 0xe9:  // Tie
			work[ch].flags |= SKIP_KEYOFF;
			cont = true;
			break;

		case 0xe7:  // Frq LFO
			if (!need(8)) break;
			work[ch].flfo_params.delay   = code[0] | code[1] << 8;
			work[ch].flfo_params.counter = code[2] | code[3] << 8;
			work[ch].flfo_params.value   = code[4] | code[5] << 8;
			work[ch].flfo_params.max     = code[6] | code[7] << 8;
			code += 8;
			work[ch].flags |= SW_FRQLFO_ENABLED;
			work[ch].flags &= ~FRQLFO_DIRECTION;
			SetFLFO(ch, false);
			cont = true;
			break;

		case 0xe6:  // Vol LFO (PSG)
			if (!need(8)) break;
			work[ch].vlfo_params.delay   = code[0] | code[1] << 8;
			work[ch].vlfo_params.counter = code[2] | code[3] << 8;
			work[ch].vlfo_params.value   = code[4] | code[5] << 8;
			work[ch].vlfo_params.max     = code[6] | code[7] << 8;
			code += 8;
			work[ch].fm_delay = work[ch].vlfo_params.delay;
			work[ch].sw_lfo_val = 0;
			work[ch].flags |= SW_VOLLFO_ENABLED;
			work[ch].flags &= ~VOLLFO_DIRECTION;
			SetVLFO(ch, false);
			cont = true;
			break;

		case 0xf3:  // Default length
			{
				if (!need(1)) break;
				uint8_t len_b = *code++;
				if (len_b < 0x80)
					work[ch].default_length = len_b;
				else {
					if (!need(1)) break;
					work[ch].default_length = (len_b - 0x80) << 8 | *code++;
				}
			}
			cont = true;
			break;

		case 0xe5:  // LFO flags
			if (!need(1)) break;
			work[ch].flags &= ~0x70;
			work[ch].flags |= (*code++ & 7) << 4;
			cont = true;
			break;

		case 0xe8:  // Vol LFO (FM)
			if (!need(10)) break;
			work[ch].vlfo_params.delay   = code[0] | code[1] << 8;
			work[ch].vlfo_params.counter = code[2] | code[3] << 8;
			work[ch].vlfo_params.value   = code[4] | code[5] << 8;
			work[ch].vlfo_params.max     = code[6] | code[7] << 8;
			work[ch].fm_delay            = code[8] | code[9] << 8;
			code += 10;
			work[ch].flags |= SW_VOLLFO_ENABLED;
			cont = true;
			break;

		case 0xf4:  // Tempo
			if (!need(1)) break;
			{
				uint8_t t = *code++;
				tempo = t ? t : 120;
			}
			cont = true;
			break;

		case 0xf5:  // FM tone set
			if (!need(1)) break;
			work[ch].tone_num = *code++;
			{
				size_t tone_pos = static_cast<size_t>(tone_offset) + static_cast<size_t>(TONE_DATA_SIZE) * work[ch].tone_num;
				if (tone_pos + TONE_DATA_SIZE > data.size()) {
					work[ch].part_enable = PART_DISABLED;
					part_looped |= 1 << ch;
					break;
				}
				SetFMTone(ch, &data[tone_pos]);
			}
			ExecHWLFO(ch);
			cont = true;
			break;

		case 0xf7:  // Temp octave--
			work[ch].octave_temp = work[ch].octave - 1;
			work[ch].use_temp_octave = 1;
			cont = true;
			break;

		case 0xf8:  // Temp octave++
			work[ch].octave_temp = work[ch].octave + 1;
			work[ch].use_temp_octave = 1;
			cont = true;
			break;

		case 0xf9:  // Absolute vol
			if (!need(1)) break;
			work[ch].vol = work[ch].tl4_vol = std::min(*code++, static_cast<uint8_t>(127));
			SetVol(ch);
			cont = true;
			break;

		case 0xe1:  // Relative vol
			if (!need(1)) break;
			work[ch].vol = work[ch].tl4_vol =
				std::min(static_cast<uint8_t>(work[ch].vol + *code++),
						 static_cast<uint8_t>(127));
			SetVol(ch);
			cont = true;
			break;

		case 0xe4:  // HW_LFO
			if (!need(3)) break;
			hwlfo = *code++ | 8;
			work[ch].pms_param = *code++;
			work[ch].ams_param = *code++;
			work[ch].flags |= USE_HW_LFO;
			ExecHWLFO(ch);
			cont = true;
			break;

		case 0xfc:  // Detune
			if (!need(1)) break;
			work[ch].detune = *(const int8_t*)code++;
			cont = true;
			break;

		case 0xfe:
			if (!need(3)) break;
			code += 3;
			cont = true;
			break;

		case 0xf6:
			if (!need(2)) break;
			code += 2;
			cont = true;
			break;

		case 0xfa:
		case 0xfd:
			if (!need(1)) break;
			code++;
			cont = true;
			break;

		case 0xe0:
			if (!need(1)) break;
			current_mark = *code++;
			cont = true;
			break;

		default:
			WARNING("MakoFM: unknown command 0x%02x", a);
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			break;
		}
	}
	return code;
}

const uint8_t* MakoFM::ExecNote(int ch, const uint8_t* code) {
	const uint8_t* const end = data.data() + data.size();
	if (code < data.data() || code >= end) {
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return code;
	}
	uint8_t note = *code++;
	if (note < 13) {
		if (code >= end) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			return code;
		}
		work[ch].note = note;
		work[ch].length = *code++;
	} else if (note < 26) {
		if (static_cast<size_t>(end - code) < 2) {
			work[ch].part_enable = PART_DISABLED;
			part_looped |= 1 << ch;
			return code;
		}
		work[ch].note = note - 13;
		work[ch].length = code[0] | code[1] << 8;
		code += 2;
	} else if (0x80 <= note && note < 0x80 + 13) {
		work[ch].note = note - 0x80;
		work[ch].length = work[ch].default_length;
	} else {
		WARNING("MakoFM: unknown command: 0x%02x", note);
		work[ch].part_enable = PART_DISABLED;
		part_looped |= 1 << ch;
		return code;
	}

	if (!work[ch].use_temp_octave)
		work[ch].octave_temp = work[ch].octave;
	work[ch].use_temp_octave = 0;

	uint16_t freq = 0;
	if (work[ch].note) {
		if (IsFMchannel(ch))
			freq = FMFreqTable[work[ch].note - 1] | ((work[ch].octave_temp & 7) << 11);
		else
			freq = PSGFreqTable[work[ch].note - 1] >> (work[ch].octave_temp & 7);
	}]=]
  "MakoFM::ExecCmd and ExecNote bounds checks")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/fm/makofm.cpp" CONTENT "${patched_makofm_cpp}" @ONLY)

# --- Patch generic/mako.cpp ---
set(orig_mako_cpp "${src}/generic/mako.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_mako_cpp}")
file(READ "${orig_mako_cpp}" patched_mako_cpp)
alicehub_patch_replace(patched_mako_cpp
  [=[	midi = std::make_unique<MAKOMidi>(config.midi_device);
	if (!midi->is_available())
		use_fm = true;]=]
  [=[	midi = std::make_unique<MAKOMidi>(config.midi_device);
	if (g_device && !midi->is_available())
		use_fm = true;]=]
  "MAKO::MAKO !g_device guard")
alicehub_patch_replace(patched_mako_cpp
  [=[	if (g_device)
		SDL_CloseAudioDevice(g_device);
	SDL_QuitSubSystem(SDL_INIT_AUDIO);]=]
  [=[	if (g_device) {
		SDL_CloseAudioDevice(g_device);
		g_device = 0;
	}
	SDL_QuitSubSystem(SDL_INIT_AUDIO);]=]
  "MAKO::~MAKO reset g_device")
alicehub_patch_replace(patched_mako_cpp
  [=[	} else if (use_fm) {
		std::vector<uint8_t> data = amus.load(page);
		if (data.empty())
			return;
		auto f = std::make_unique<MakoYmfm>(SAMPLE_RATE, std::move(data));]=]
  [=[	} else if (use_fm) {
		if (!g_device)
			return;
		std::vector<uint8_t> data = amus.load(page);
		if (data.size() < 4 + 9 * 4 ||
		    (data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 0))
			return;
		auto f = std::make_unique<MakoYmfm>(SAMPLE_RATE, std::move(data));]=]
  "MAKO::play_music non-MAKO data validation")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/mako.cpp" CONTENT "${patched_mako_cpp}" @ONLY)

# --- Patch encoding.cpp ---
set(orig_encoding_cpp "${src}/encoding.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_encoding_cpp}")
file(READ "${orig_encoding_cpp}" patched_encoding_cpp)
alicehub_patch_replace(patched_encoding_cpp
  [=[	int next_codepoint(std::string_view& s) override
	{
		int code = (unsigned char)s[0];
		if (is_2byte(code)) {
			code = (code << 8) | (unsigned char)s[1];
			s.remove_prefix(2);
		} else {
			s.remove_prefix(1);
		}
		return sjis_to_unicode(code);
	}]=]
  [=[	int next_codepoint(std::string_view& s) override
	{
		if (s.empty())
			return 0;
		int code = (unsigned char)s[0];
		if (is_2byte(code) && s.size() >= 2) {
			code = (code << 8) | (unsigned char)s[1];
			s.remove_prefix(2);
		} else {
			s.remove_prefix(1);
		}
		return sjis_to_unicode(code);
	}]=]
  "SjisEncoding::next_codepoint bounds check")
alicehub_patch_replace(patched_encoding_cpp
  [=[			int u;
			if (*src <= 0xdf) {
				u = (src[0] & 0x1f) << 6 | (src[1] & 0x3f);
				src += 2;
			} else if (*src <= 0xef) {
				u = (src[0] & 0xf) << 12 | (src[1] & 0x3f) << 6 | (src[2] & 0x3f);
				src += 3;
			} else {]=]
  [=[			int u;
			if (*src <= 0xdf) {
				if (src + 2 > end || (src[1] & 0xc0) != 0x80) {
					result += '?';
					src++;
					continue;
				}
				u = (src[0] & 0x1f) << 6 | (src[1] & 0x3f);
				src += 2;
			} else if (*src <= 0xef) {
				if (src + 3 > end || (src[1] & 0xc0) != 0x80 || (src[2] & 0xc0) != 0x80) {
					result += '?';
					src++;
					continue;
				}
				u = (src[0] & 0xf) << 12 | (src[1] & 0x3f) << 6 | (src[2] & 0x3f);
				src += 3;
			} else {]=]
  "SjisEncoding::fromUtf8 truncated UTF-8 guard")
alicehub_patch_replace(patched_encoding_cpp
  [=[			if (*src >= 0xa0 && *src <= 0xdf) {
				c = 0xff60 + *src - 0xa0;
				src++;
			} else {
				c = s2u[*src - 0x80][*(src+1) - 0x40];
				src += 2;
			}]=]
  [=[			if (*src >= 0xa0 && *src <= 0xdf) {
				c = 0xff60 + *src - 0xa0;
				src++;
			} else if (src + 1 < end && src[1] >= 0x40) {
				c = s2u[*src - 0x80][*(src+1) - 0x40];
				src += 2;
			} else {
				c = '?';
				src += (src + 1 < end) ? 2 : 1;
			}]=]
  "SjisEncoding::toUtf8 trail byte guard")
alicehub_patch_replace(patched_encoding_cpp
  [=[		return s2u[(code >> 8) - 0x80][(code & 0xff) - 0x40];]=]
  [=[		uint8_t hi = static_cast<uint8_t>(code >> 8);
		uint8_t lo = static_cast<uint8_t>(code & 0xff);
		if (hi < 0x80 || lo < 0x40)
			return '?';
		return s2u[hi - 0x80][lo - 0x40];]=]
  "SjisEncoding::sjis_to_unicode bounds check")
alicehub_patch_replace(patched_encoding_cpp
  [=[	int next_codepoint(std::string_view& s) override
	{
		int code;
		unsigned char c = s[0];

		if (c <= 0x7f) {
			code = c;
			s.remove_prefix(1);
		} else if (c <= 0xbf) {
			// Invalid UTF-8 sequence
			code = '?';
			s.remove_prefix(1);
		} else if (c <= 0xdf) {
			code = (s[0] & 0x1f) << 6 | (s[1] & 0x3f);
			s.remove_prefix(2);
		} else if (c <= 0xef) {
			code = (s[0] & 0xf) << 12 | (s[1] & 0x3f) << 6 | (s[2] & 0x3f);
			s.remove_prefix(3);
		} else if (c <= 0xf7) {
			code = (s[0] & 0x7) << 18 | (s[1] & 0x3f) << 12 | (s[2] & 0x3f) << 6 | (s[3] & 0x3f);
			s.remove_prefix(4);
		} else {]=]
  [=[	int next_codepoint(std::string_view& s) override
	{
		if (s.empty())
			return 0;
		int code;
		unsigned char c = s[0];

		if (c <= 0x7f) {
			code = c;
			s.remove_prefix(1);
		} else if (c <= 0xbf) {
			// Invalid UTF-8 sequence
			code = '?';
			s.remove_prefix(1);
		} else if (c <= 0xdf) {
			if (s.size() < 2 || (static_cast<unsigned char>(s[1]) & 0xc0) != 0x80) {
				code = 0xfffd;
				s.remove_prefix(1);
			} else {
				code = (s[0] & 0x1f) << 6 | (s[1] & 0x3f);
				s.remove_prefix(2);
			}
		} else if (c <= 0xef) {
			if (s.size() < 3 ||
			    (static_cast<unsigned char>(s[1]) & 0xc0) != 0x80 ||
			    (static_cast<unsigned char>(s[2]) & 0xc0) != 0x80) {
				code = 0xfffd;
				s.remove_prefix(1);
			} else {
				code = (s[0] & 0xf) << 12 | (s[1] & 0x3f) << 6 | (s[2] & 0x3f);
				s.remove_prefix(3);
			}
		} else if (c <= 0xf7) {
			if (s.size() < 4 ||
			    (static_cast<unsigned char>(s[1]) & 0xc0) != 0x80 ||
			    (static_cast<unsigned char>(s[2]) & 0xc0) != 0x80 ||
			    (static_cast<unsigned char>(s[3]) & 0xc0) != 0x80) {
				code = 0xfffd;
				s.remove_prefix(1);
			} else {
				code = (s[0] & 0x7) << 18 | (s[1] & 0x3f) << 12 | (s[2] & 0x3f) << 6 | (s[3] & 0x3f);
				s.remove_prefix(4);
			}
		} else {]=]
  "Utf8Encoding::next_codepoint bounds check")
alicehub_patch_replace(patched_encoding_cpp
  [=[int Encoding::mbslen(std::string_view s)
{
	int len = 0;
	while (!s.empty()) {
		int n = mblen((unsigned char)s[0]);
		s.remove_prefix(n);
		len++;
	}
	return len;
}]=]
  [=[int Encoding::mbslen(std::string_view s)
{
	int len = 0;
	while (!s.empty()) {
		size_t n = static_cast<size_t>(mblen((unsigned char)s[0]));
		s.remove_prefix(n < s.size() ? n : s.size());
		len++;
	}
	return len;
}]=]
  "Encoding::mbslen bounds check")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/encoding.cpp" CONTENT "${patched_encoding_cpp}" @ONLY)

# --- Patch sys/scenario.h and sys/scenario.cpp (bounds-checked dynamic string reads) ---
set(orig_scenario_h "${src}/sys/scenario.h")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_scenario_h}")
file(READ "${orig_scenario_h}" patched_scenario_h)
alicehub_patch_replace(patched_scenario_h
  [=[#include <utility>
#include <vector>
#include <stdint.h>
#include "dri.h"]=]
  [=[#include <string>
#include <utility>
#include <vector>
#include <stdint.h>
#include "dri.h"]=]
  "Scenario::scenario.h include <string>")
alicehub_patch_replace(patched_scenario_h
  [=[	uint8_t getd() { return data_[addr_++]; }
	uint16_t getw() {
		uint16_t w = data_[addr_++];
		w |= data_[addr_++] << 8;
		return w;
	}
	void ungetd() { addr_--; }
	void skip_syseng_string(Encoding *enc, uint8_t terminator);
	void get_syseng_string(char* buf, int size, Encoding *enc, uint8_t terminator);]=]
  [=[	bool eof() const { return !data_ || addr_ < 0 || static_cast<size_t>(addr_) >= data_size_; }
	uint8_t getd() { return data_[addr_++]; }
	uint8_t getd_checked();
	uint16_t getw() {
		uint16_t w = data_[addr_++];
		w |= data_[addr_++] << 8;
		return w;
	}
	void ungetd() { addr_--; }
	void skip_syseng_string(Encoding *enc, uint8_t terminator);
	void get_syseng_string(char* buf, int size, Encoding *enc, uint8_t terminator);
	std::string get_syseng_string_dynamic(Encoding *enc, uint8_t terminator);]=]
  "Scenario bounded string API decls")
alicehub_patch_replace(patched_scenario_h
  [=[	const uint8_t* data_;
	size_t data_size_;
	int page_;
	int addr_;
	int cmd_addr_;]=]
  [=[	const uint8_t* data_ = nullptr;
	size_t data_size_ = 0;
	int page_ = 0;
	int addr_ = 0;
	int cmd_addr_ = 0;]=]
  "Scenario member default initializers")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/scenario.h" CONTENT "${patched_scenario_h}" @ONLY)

set(orig_scenario_cpp "${src}/sys/scenario.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_scenario_cpp}")
file(READ "${orig_scenario_cpp}" patched_scenario_cpp)
alicehub_patch_replace(patched_scenario_cpp
  [=[void Scenario::skip_syseng_string(Encoding *enc, uint8_t terminator)
{
	for (uint8_t c = getd(); c != terminator; c = getd()) {
		if (c == '\\')
			c = getd();
		skip(enc->mblen(c) - 1);
	}
}

void Scenario::get_syseng_string(char* buf, int size, Encoding *enc, uint8_t terminator)
{
	int i = 0;
	for (uint8_t c = getd(); c != terminator; c = getd()) {
		if (c == '\\')
			c = getd();
		int len = enc->mblen(c);
		if (i + len >= size)
			sys_error("String buffer overrun at %d:%04x", page_, cmd_addr_);
		buf[i++] = c;
		for (int j = 1; j < len; ++j)
			buf[i++] = getd();
	}
	buf[i] = '\0';
}]=]
  [=[uint8_t Scenario::getd_checked()
{
	if (!data_ || addr_ < 0 || static_cast<size_t>(addr_) >= data_size_)
		sys_error("Scenario error: unexpected end of page at %d:%04x", page_, cmd_addr_);
	return data_[addr_++];
}

void Scenario::skip_syseng_string(Encoding *enc, uint8_t terminator)
{
	for (;;) {
		uint8_t c = getd_checked();
		if (c == terminator)
			break;
		if (c == '\\')
			c = getd_checked();
		int len = enc->mblen(c);
		for (int j = 1; j < len; ++j)
			(void)getd_checked();
	}
}

void Scenario::get_syseng_string(char* buf, int size, Encoding *enc, uint8_t terminator)
{
	int i = 0;
	for (;;) {
		uint8_t c = getd_checked();
		if (c == terminator)
			break;
		if (c == '\\')
			c = getd_checked();
		int len = enc->mblen(c);
		if (i + len >= size)
			sys_error("String buffer overrun at %d:%04x", page_, cmd_addr_);
		buf[i++] = c;
		for (int j = 1; j < len; ++j)
			buf[i++] = getd_checked();
	}
	buf[i] = '\0';
}

std::string Scenario::get_syseng_string_dynamic(Encoding *enc, uint8_t terminator)
{
	std::string out;
	for (;;) {
		uint8_t c = getd_checked();
		if (c == terminator)
			break;
		if (c == '\\')
			c = getd_checked();
		int len = enc->mblen(c);
		out.push_back(static_cast<char>(c));
		for (int j = 1; j < len; ++j)
			out.push_back(static_cast<char>(getd_checked()));
	}
	return out;
}]=]
  "Scenario::getd_checked and bounded syseng string helpers")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/scenario.cpp" CONTENT "${patched_scenario_cpp}" @ONLY)
list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/scenario.cpp")

# Copy nact.h and remaining sys/*.cpp that include nact.h so every translation unit sees patched scenario.h
foreach(_sys_copy nact.h msgskip.cpp nact_sys1.cpp nact_gakuenking.cpp)
  set(_sys_copy_src "${src}/sys/${_sys_copy}")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_sys_copy_src}")
  configure_file("${_sys_copy_src}" "${CMAKE_CURRENT_BINARY_DIR}/${_sys_copy}" COPYONLY)
endforeach()
list(APPEND vm_sources
  "${CMAKE_CURRENT_BINARY_DIR}/msgskip.cpp"
  "${CMAKE_CURRENT_BINARY_DIR}/nact_sys1.cpp"
  "${CMAKE_CURRENT_BINARY_DIR}/nact_gakuenking.cpp")

# --- Patch sys/nact.cpp ---
set(orig_nact_cpp "${src}/sys/nact.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_nact_cpp}")
file(READ "${orig_nact_cpp}" patched_nact_cpp)
alicehub_patch_replace(patched_nact_cpp
  [=[extern SDL_Window* g_window;]=]
  [=[extern SDL_Window* g_window;
void system3_reset_input_state();
bool ags_pc98_font_is_gb2312_for_encoding(const char* encoding);]=]
  "NACT input state reset decl")
alicehub_patch_replace(patched_nact_cpp
  [=[	ags = new AGS(config, game_id);]=]
  [=[	ags = new AGS(config, game_id);
	if (ags_pc98_font_is_gb2312_for_encoding(game_id.encoding) && game_id.language != ENGLISH) {
		if (strings.back == encoding->fromUtf8(u8"戻る"))
			strings.back = "\x8c\x54\x8e\x77";
		if (strings.next_page == encoding->fromUtf8(u8"次のページ"))
			strings.next_page = "\x98\x61\x99\xb9\x99\xb1";
	}]=]
  "NACT::NACT Chinese PC-98 FONT.bmp default menu strings")
alicehub_patch_replace(patched_nact_cpp
  [=[	platform_initialize();]=]
  [=[	system3_reset_input_state();
	platform_initialize();]=]
  "NACT::NACT reset input state")
alicehub_patch_replace(patched_nact_cpp
  [=[NACT::~NACT()
{
	delete ags;
	delete mako;
	delete msgskip;

	platform_finalize();
}]=]
  [=[NACT::~NACT()
{
	if (sdl_gamecontroller) {
		SDL_GameControllerClose(sdl_gamecontroller);
		sdl_gamecontroller = NULL;
	}
	delete ags;
	delete mako;
	delete msgskip;

	platform_finalize();
}]=]
  "NACT::~NACT close gamecontroller")
alicehub_patch_replace(patched_nact_cpp
  [=[	char buf[200];
	if (first_byte == '\'' || first_byte == '"') {  // SysEng
		sco.get_syseng_string(buf, sizeof(buf), encoding.get(), first_byte);
	} else {
		int i = 0;
		uint8_t c = first_byte;
		while (is_message(c)) {
			int len = encoding->mblen(c);
			buf[i++] = c;
			for (int j = 1; j < len; ++j)
				buf[i++] = sco.getd();
			c = sco.getd();
		}
		sco.ungetd();
		buf[i] = '\0';
	}]=]
  [=[	std::string buf;
	if (first_byte == '\'' || first_byte == '"') {  // SysEng
		buf = sco.get_syseng_string_dynamic(encoding.get(), first_byte);
	} else {
		uint8_t c = first_byte;
		while (is_message(c)) {
			int len = encoding->mblen(c);
			buf.push_back(static_cast<char>(c));
			for (int j = 1; j < len; ++j)
				buf.push_back(static_cast<char>(sco.getd_checked()));
			if (sco.eof())
				break;
			c = sco.getd_checked();
			if (!is_message(c)) {
				sco.ungetd();
				break;
			}
		}
	}]=]
  "NACT::message std::string dynamic buffer")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/nact.cpp" CONTENT "${patched_nact_cpp}" @ONLY)
list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact.cpp")

# --- Patch sys/nact_sys2.cpp ---
set(orig_nact_sys2 "${src}/sys/nact_sys2.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_nact_sys2}")
file(READ "${orig_nact_sys2}" patched_nact_sys2)
alicehub_patch_replace(patched_nact_sys2
  [=[void NACT_Sys2::cmd_m()
{
	char string[33];

	int d = sco.getd();
	if (d == '\'' || d == '"') {  // SysEng
		sco.get_syseng_string(string, sizeof(string), encoding.get(), d);
	} else {
		int p = 0;
		while(d != ':') {
			string[p++] = d;
			d = sco.getd();
		}
		string[p] = '\0';
	}]=]
  [=[void NACT_Sys2::cmd_m()
{
	std::string string;

	int d = sco.getd_checked();
	if (d == '\'' || d == '"') {  // SysEng
		string = sco.get_syseng_string_dynamic(encoding.get(), static_cast<uint8_t>(d));
	} else {
		while (d != ':') {
			string.push_back(static_cast<char>(d));
			d = sco.getd_checked();
		}
	}]=]
  "NACT_Sys2::cmd_m std::string dynamic buffer")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/nact_sys2.cpp" CONTENT "${patched_nact_sys2}" @ONLY)
list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact_sys2.cpp")

# --- Patch sys/nact_sys3.cpp ---
set(orig_nact_sys3 "${src}/sys/nact_sys3.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_nact_sys3}")
file(READ "${orig_nact_sys3}" patched_nact_sys3)
alicehub_patch_replace(patched_nact_sys3
  [=[void NACT_Sys3::cmd_m()
{
	char string[22];

	int d = sco.getd();
	if (d == '\'' || d == '"') {  // SysEng
		sco.get_syseng_string(string, sizeof(string), encoding.get(), d);
	} else {
		int p = 0;
		while(d != ':') {
			string[p++] = d;
			d = sco.getd();
		}
		string[p] = '\0';
	}]=]
  [=[void NACT_Sys3::cmd_m()
{
	std::string string;

	int d = sco.getd_checked();
	if (d == '\'' || d == '"') {  // SysEng
		string = sco.get_syseng_string_dynamic(encoding.get(), static_cast<uint8_t>(d));
	} else {
		while (d != ':') {
			string.push_back(static_cast<char>(d));
			d = sco.getd_checked();
		}
	}]=]
  "NACT_Sys3::cmd_m std::string dynamic buffer")
alicehub_patch_replace(patched_nact_sys3
  [=[	} else if(tvar_index == 31) {
		sco.open(string);
	} else if(tvar_index == 32) {
		ags->set_cg_file(string);
	} else if(tvar_index == 33) {
		mako->amus.open(string);
		strcpy(string + strlen(string) - 3, "MDA");
		mako->mda.open(string);]=]
  [=[	} else if(tvar_index == 31) {
		sco.open(encoding->toUtf8(string).c_str());
	} else if(tvar_index == 32) {
		ags->set_cg_file(encoding->toUtf8(string).c_str());
	} else if(tvar_index == 33) {
		std::string utf8_name = encoding->toUtf8(string);
		mako->amus.open(utf8_name.c_str());
		if (utf8_name.size() >= 3) {
			utf8_name.replace(utf8_name.size() - 3, 3, "MDA");
		}
		mako->mda.open(utf8_name.c_str());]=]
  "System 3 UTF-8 cmd_m filename patch")
alicehub_patch_replace(patched_nact_sys3
  [=[		case 232:
			if(ags->screen_height != (param ? 480 : 400)) {
				// TODO: fix
				// SendMessage(g_hwnd, WM_USER + 2, param ? 480 : 400, 0);
				ags->screen_height = param ? 480 : 400;
			}
			break;]=]
  [=[		case 232:
			if(ags->screen_height != (param ? 480 : 400)) {
				ags->screen_height = param ? 480 : 400;
				ags->window_height = ags->screen_height;
				ags->invalidate_screen(0, 0, ags->screen_width, ags->screen_height);
			}
			break;]=]
  "System 3 Y 232 480p screen height resize")
alicehub_patch_replace(patched_nact_sys3
  [=[		case 254:
			RND = (game_id.is(GameId::YAKATA3_CD) || game_id.is(GameId::YAKATA3_FD) || game_id.is(GameId::NINGYO)) ? 1 : 0;
			break;]=]
  [=[		case 254:
			RND = (game_id.is(GameId::YAKATA3_CD) || game_id.is(GameId::YAKATA3_FD) || game_id.is(GameId::NINGYO) || ((game_id.is(GameId::PROG_CD) || game_id.is(GameId::MUGEN)) && mako->is_cd_available())) ? 1 : 0;
			break;]=]
  "System 3 Prostudent G CD-DA Y254 patch")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/nact_sys3.cpp" CONTENT "${patched_nact_sys3}" @ONLY)
list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact_sys3.cpp")

# --- Patch sys/ags_text.cpp ---
set(orig_ags_text "${src}/sys/ags_text.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_ags_text}")
file(READ "${orig_ags_text}" patched_ags_text)
alicehub_patch_replace(patched_ags_text
  [=[#include "encoding.h"
#include "texthook.h"

namespace {]=]
  [=[#include "encoding.h"
#include "fileio.h"
#include "texthook.h"
#include <string.h>
#include <vector>

namespace {

struct Pc98BitmapFont {
	static constexpr int WIDTH = 2048;
	static constexpr int HEIGHT = 2048;
	static constexpr int ROW_BYTES = WIDTH / 8;
	bool loaded = false;
	bool is_gb2312 = false;
	std::vector<uint8_t> bits;

	bool pixel(int x, int y) const {
		if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT)
			return false;
		return (bits[static_cast<size_t>(y) * ROW_BYTES + (x >> 3)] & (0x80 >> (x & 7))) != 0;
	}
};

Pc98BitmapFont g_pc98_font;

uint16_t rd_le16(const uint8_t* p) {
	return static_cast<uint16_t>(p[0] | (p[1] << 8));
}

uint32_t rd_le32(const uint8_t* p) {
	return static_cast<uint32_t>(p[0]) |
	       (static_cast<uint32_t>(p[1]) << 8) |
	       (static_cast<uint32_t>(p[2]) << 16) |
	       (static_cast<uint32_t>(p[3]) << 24);
}

bool is_utf8_encoding_name(const char* enc) {
	if (!enc)
		return false;
	auto eq_ci = [](const char* a, const char* b) {
		while (*a && *b) {
			unsigned char ca = static_cast<unsigned char>(*a++);
			unsigned char cb = static_cast<unsigned char>(*b++);
			if (ca >= 'A' && ca <= 'Z') ca += 'a' - 'A';
			if (cb >= 'A' && cb <= 'Z') cb += 'a' - 'A';
			if (ca != cb) return false;
		}
		return *a == '\0' && *b == '\0';
	};
	return eq_ci(enc, "UTF-8") || eq_ci(enc, "UTF8");
}

bool pc98_cell_nonempty(int cx, int ry) {
	int gx = cx * 16;
	int gy = ry * 16;
	for (int dy = 0; dy < 16; dy++) {
		size_t row_off = static_cast<size_t>(gy + dy) * Pc98BitmapFont::ROW_BYTES + (gx >> 3);
		if (g_pc98_font.bits[row_off] != 0 || g_pc98_font.bits[row_off + 1] != 0)
			return true;
	}
	return false;
}

bool ank_to_zenkaku_jis(uint8_t b, bool is_gb2312, int& j1, int& j2) {
	if (b == 0x20) {
		j1 = 0x21;
		j2 = 0x21;
		return true;
	}
	if (b >= 0x21 && b <= 0x7e) {
		if (is_gb2312 ||
		    (b >= '0' && b <= '9') ||
		    (b >= 'A' && b <= 'Z') ||
		    (b >= 'a' && b <= 'z')) {
			j1 = 0x23;
			j2 = b;
			return true;
		}
		return false;
	}
	switch (b) {
	case 0xa1: j1 = 0x21; j2 = 0x23; return true; // 。
	case 0xa2: j1 = 0x21; j2 = is_gb2312 ? 0x38 : 0x56; return true; // 「
	case 0xa3: j1 = 0x21; j2 = is_gb2312 ? 0x39 : 0x57; return true; // 」
	case 0xa4: j1 = 0x21; j2 = 0x22; return true; // 、
	case 0xa5: j1 = 0x21; j2 = is_gb2312 ? 0x24 : 0x26; return true; // ・ / ·
	default: break;
	}
	if (b >= 0xa6 && b <= 0xdf) {
		static const uint16_t kana_jis[58] = {
			0x2472, 0x2421, 0x2423, 0x2425, 0x2427, 0x2429, 0x2463, 0x2465,
			0x2467, 0x2443, 0x213c, 0x2422, 0x2424, 0x2426, 0x2428, 0x242a,
			0x242b, 0x242d, 0x242f, 0x2431, 0x2433, 0x2435, 0x2437, 0x2439,
			0x243b, 0x243d, 0x243f, 0x2441, 0x2444, 0x2446, 0x2448, 0x244a,
			0x244b, 0x244c, 0x244d, 0x244e, 0x244f, 0x2452, 0x2455, 0x2458,
			0x245b, 0x245e, 0x245f, 0x2460, 0x2461, 0x2462, 0x2464, 0x2466,
			0x2468, 0x2469, 0x246a, 0x246b, 0x246c, 0x246d, 0x246f, 0x2473,
			0x212b, 0x212c,
		};
		uint16_t jis = kana_jis[b - 0xa6];
		j1 = jis >> 8;
		j2 = jis & 0xff;
		return true;
	}
	return false;
}

void draw_pc98_glyph(uint8_t (*vram)[640], int dest_x, int dest_y,
                     int src_x, int src_y, int src_w, int src_h,
                     int dst_w, int dst_h, uint8 color) {
	if (dst_w <= 0 || dst_h <= 0)
		return;
	for (int y = std::max(0, -dest_y); y < dst_h && dest_y + y < 480; y++) {
		int sy = src_y + (y * src_h) / dst_h;
		for (int x = std::max(0, -dest_x); x < dst_w && dest_x + x < 640; x++) {
			int sx = src_x + (x * src_w) / dst_w;
			if (g_pc98_font.pixel(sx, sy))
				vram[dest_y + y][dest_x + x] = color;
		}
	}
}

bool gaiji_has_bitmap(const uint8_t bitmap[32]) {
	for (int i = 0; i < 32; i++) {
		if (bitmap[i] != 0)
			return true;
	}
	return false;
}]=]
  "AGS PC-98 FONT.bmp helpers")
alicehub_patch_replace(patched_ags_text
  [=[	TTF_Font* font = NULL;
	switch (ctx.font_size) {
	case 16: font = hFont16; break;
	case 24: font = hFont24; break;
	case 32: font = hFont32; break;
	case 48: font = hFont48; break;
	case 64: font = hFont64; break;
	}]=]
  [=[	if (g_pc98_font.loaded && !is_utf8_encoding_name(g_nact->game_id.encoding)) {
		const int size = ctx.font_size > 0 ? ctx.font_size : 16;
		const int half_size = std::max(1, size / 2);
		const int base_y = ctx.pos.y;
		while (!string.empty()) {
			const char* ch_start = string.data();
			uint8_t b1 = static_cast<uint8_t>(string[0]);
			bool is_space = (b1 == ' ');
			if (((b1 >= 0x81 && b1 <= 0x9f) || (b1 >= 0xe0 && b1 <= 0xfc)) && string.size() >= 2) {
				uint8_t b2 = static_cast<uint8_t>(string[1]);
				if (b2 >= 0x40 && b2 <= 0xfc && b2 != 0x7f) {
					string.remove_prefix(2);
					uint16_t sjis = static_cast<uint16_t>((b1 << 8) | b2);
					int gaiji_idx = -1;
					if (sjis >= 0xeb9f && sjis <= 0xebfc)
						gaiji_idx = sjis - 0xeb9f;
					else if (sjis >= 0xec40 && sjis <= 0xec9e)
						gaiji_idx = sjis - 0xec40 + 94;
					if (gaiji_idx >= 0 && gaiji_idx < 188 && gaiji_has_bitmap(gaiji[gaiji_idx])) {
						draw_gaiji(screen, dest_x, base_y, gaiji[gaiji_idx], size, ctx.font_color);
					} else {
						if (!draw_menu) {
							std::string_view ch_sv(ch_start, 2);
							int code = g_nact->encoding->next_codepoint(ch_sv);
							if (code > 0)
								texthook_character(g_nact->get_scenario_page(), draw_hankaku ? convert_to_hankaku(code) : convert_to_zenkaku(code));
						}
						int j1 = (b1 - (b1 <= 0x9f ? 0x81 : 0xc1)) * 2 + 0x21;
						int j2;
						if (b2 >= 0x9f) {
							j1 += 1;
							j2 = b2 - 0x7e;
						} else {
							j2 = b2 - (b2 < 0x7f ? 0x1f : 0x20);
						}
						if (j1 >= 0x21 && j1 <= 0x7e && j2 >= 0x21 && j2 <= 0x7e) {
							draw_pc98_glyph(vram[screen], dest_x, base_y, (j1 - 0x20) * 16, j2 * 16, 16, 16, size, size, ctx.font_color);
						}
					}
					dest_x += size;
					if (!draw_menu && text_wait) {
						if (screen == 0)
							invalidate_screen(ctx.pos.x, base_y, dest_x - ctx.pos.x, size);
						ctx.pos.x = dest_x;
						g_nact->text_wait();
					}
					continue;
				}
			}
			string.remove_prefix(1);
			if (!draw_menu) {
				std::string_view ch_sv(ch_start, 1);
				int code = g_nact->encoding->next_codepoint(ch_sv);
				if (code > 0)
					texthook_character(g_nact->get_scenario_page(), draw_hankaku ? convert_to_hankaku(code) : convert_to_zenkaku(code));
			}
			int j1 = 0, j2 = 0;
			if (!draw_hankaku && ank_to_zenkaku_jis(b1, g_pc98_font.is_gb2312, j1, j2)) {
				draw_pc98_glyph(vram[screen], dest_x, base_y, (j1 - 0x20) * 16, j2 * 16, 16, 16, size, size, ctx.font_color);
				dest_x += size;
			} else {
				draw_pc98_glyph(vram[screen], dest_x, base_y, static_cast<int>(b1) * 8, 0, 8, 16, half_size, size, ctx.font_color);
				dest_x += half_size;
			}
			if (!draw_menu && text_wait && !is_space) {
				if (screen == 0)
					invalidate_screen(ctx.pos.x, base_y, dest_x - ctx.pos.x, size);
				ctx.pos.x = dest_x;
				g_nact->text_wait();
			}
		}
		if (!draw_menu && !text_wait && screen == 0)
			invalidate_screen(ctx.pos.x, base_y, dest_x - ctx.pos.x, size);
		ctx.pos.x = dest_x;
		return;
	}
	TTF_Font* font = hFont16;
	switch (ctx.font_size) {
	case 16: font = hFont16; break;
	case 24: font = hFont24; break;
	case 32: font = hFont32; break;
	case 48: font = hFont48; break;
	case 64: font = hFont64; break;
	default: font = hFont16; break;
	}]=]
  "AGS::draw_text PC-98 FONT.bmp path and default font fallback")
alicehub_patch_replace(patched_ags_text
  [=[extern "C" {

void EMSCRIPTEN_KEEPALIVE ags_setAntialiasedStringMode(int on) {
	antialias = on != 0;
}

}]=]
  [=[void ags_pc98_font_unload()
{
	g_pc98_font.bits.clear();
	g_pc98_font.bits.shrink_to_fit();
	g_pc98_font.loaded = false;
	g_pc98_font.is_gb2312 = false;
}

bool ags_pc98_font_is_gb2312()
{
	return g_pc98_font.loaded && g_pc98_font.is_gb2312;
}

bool ags_pc98_font_is_gb2312_for_encoding(const char* encoding)
{
	return g_pc98_font.loaded && g_pc98_font.is_gb2312 && !is_utf8_encoding_name(encoding);
}

void ags_pc98_font_load()
{
	ags_pc98_font_unload();
	auto fio = FILEIO::open("FONT.BMP", FILEIO_READ_BINARY);
	if (!fio)
		return;
	uint8_t hdr[62];
	if (!fio->read(hdr, sizeof(hdr)))
		return;
	if (hdr[0] != 'B' || hdr[1] != 'M')
		return;
	uint32_t data_off = rd_le32(hdr + 10);
	uint32_t dib_size = rd_le32(hdr + 14);
	int32_t width = static_cast<int32_t>(rd_le32(hdr + 18));
	int32_t height = static_cast<int32_t>(rd_le32(hdr + 22));
	uint16_t planes = rd_le16(hdr + 26);
	uint16_t bpp = rd_le16(hdr + 28);
	uint32_t compression = rd_le32(hdr + 30);
	if (dib_size < 40 || width != Pc98BitmapFont::WIDTH ||
	    (height != Pc98BitmapFont::HEIGHT && height != -Pc98BitmapFont::HEIGHT) ||
	    planes != 1 || bpp != 1 || compression != 0 || data_off < 14 + dib_size + 8)
		return;
	uint8_t pal[8];
	fio->seek(static_cast<long>(14 + dib_size), SEEK_SET);
	if (!fio->read(pal, sizeof(pal)))
		return;
	int lum0 = pal[2] * 30 + pal[1] * 59 + pal[0] * 11;
	int lum1 = pal[6] * 30 + pal[5] * 59 + pal[4] * 11;
	bool invert = lum0 < lum1;
	std::vector<uint8_t> raw(static_cast<size_t>(Pc98BitmapFont::ROW_BYTES) * Pc98BitmapFont::HEIGHT);
	fio->seek(static_cast<long>(data_off), SEEK_SET);
	if (!fio->read(raw.data(), raw.size()))
		return;
	g_pc98_font.bits.resize(raw.size());
	bool top_down = height < 0;
	for (int y = 0; y < Pc98BitmapFont::HEIGHT; y++) {
		int src_y = top_down ? y : (Pc98BitmapFont::HEIGHT - 1 - y);
		const uint8_t* sp = raw.data() + static_cast<size_t>(src_y) * Pc98BitmapFont::ROW_BYTES;
		uint8_t* dp = g_pc98_font.bits.data() + static_cast<size_t>(y) * Pc98BitmapFont::ROW_BYTES;
		if (invert) {
			for (int x = 0; x < Pc98BitmapFont::ROW_BYTES; x++)
				dp[x] = static_cast<uint8_t>(~sp[x]);
		} else {
			memcpy(dp, sp, Pc98BitmapFont::ROW_BYTES);
		}
	}
	int gb2312_ext_cells = 0;
	for (int cx = 85; cx <= 87; cx++) {
		for (int ry = 0x21; ry <= 0x7e; ry++) {
			if (pc98_cell_nonempty(cx, ry))
				gb2312_ext_cells++;
		}
	}
	g_pc98_font.is_gb2312 = gb2312_ext_cells >= 32;
	g_pc98_font.loaded = true;
}

extern "C" {

void EMSCRIPTEN_KEEPALIVE ags_setAntialiasedStringMode(int on) {
	antialias = on != 0;
}

}]=]
  "AGS PC-98 FONT.bmp loader and lifecycle")
alicehub_patch_replace(patched_ags_text
  [=[	SDL_Surface* fs = TTF_RenderGlyph_Solid(font, code, white);

	// パターン出力
	for (int y = std::max(0, -dest_y); y < fs->h && dest_y + y < 480; y++) {
		uint8_t* pattern = (uint8_t*)fs->pixels + fs->pitch * y;
		for(int x = 0; x < fs->w && dest_x + x < 640; x++) {]=]
  [=[	SDL_Surface* fs = TTF_RenderGlyph_Solid(font, code, white);
	if (!fs)
		return;

	// パターン出力
	for (int y = std::max(0, -dest_y); y < fs->h && dest_y + y < 480; y++) {
		uint8_t* pattern = (uint8_t*)fs->pixels + fs->pitch * y;
		for(int x = std::max(0, -dest_x); x < fs->w && dest_x + x < 640; x++) {]=]
  "AGS::draw_char NULL glyph surface guard")
alicehub_patch_replace(patched_ags_text
  [=[	SDL_Surface* fs = TTF_RenderGlyph_Shaded(font, code, white, black);

	// パターン出力
	for (int y = std::max(0, -dest_y); y < fs->h && dest_y + y < 480; y++) {
		uint8_t* pattern = (uint8_t*)fs->pixels + fs->pitch * y;
		uint8_t* dp = &vram[dest][dest_y + y][dest_x];
		for(int x = 0; x < fs->w && dest_x + x < 640; x++, dp++) {]=]
  [=[	SDL_Surface* fs = TTF_RenderGlyph_Shaded(font, code, white, black);
	if (!fs)
		return;

	// パターン出力
	for (int y = std::max(0, -dest_y); y < fs->h && dest_y + y < 480; y++) {
		uint8_t* pattern = (uint8_t*)fs->pixels + fs->pitch * y;
		int x0 = std::max(0, -dest_x);
		uint8_t* dp = &vram[dest][dest_y + y][dest_x + x0];
		for(int x = x0; x < fs->w && dest_x + x < 640; x++, dp++) {]=]
  "AGS::draw_char_antialias NULL glyph surface guard")
file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/ags_text.cpp" CONTENT "${patched_ags_text}" @ONLY)
list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/ags_text.cpp")

# --- Patch sys/ags.cpp (common fixes; Switch overlay patch layers on top below) ---
set(orig_ags_cpp "${src}/sys/ags.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_ags_cpp}")
file(READ "${orig_ags_cpp}" patched_ags_cpp)
alicehub_patch_replace(patched_ags_cpp
  [=[extern SDL_Window* g_window;
extern SDL_Renderer* g_renderer;]=]
  [=[extern SDL_Window* g_window;
extern SDL_Renderer* g_renderer;
void ags_pc98_font_load();
void ags_pc98_font_unload();]=]
  "AGS PC-98 FONT.bmp decls")
alicehub_patch_replace(patched_ags_cpp
  [=[	fio.reset();

	// SYSTEM3 初期化]=]
  [=[	fio.reset();
	ags_pc98_font_load();

	// SYSTEM3 初期化]=]
  "AGS::AGS load PC-98 FONT.bmp")
alicehub_patch_replace(patched_ags_cpp
  [=[	if (config.no_antialias)
		ags_setAntialiasedStringMode(0);]=]
  [=[	ags_setAntialiasedStringMode(!config.no_antialias);]=]
  "AGS::AGS always reset antialias static")
alicehub_patch_replace(patched_ags_cpp
  [=[	// フォント開放
	if (rw_font) {
		TTF_CloseFont(hFont16);
		TTF_CloseFont(hFont24);
		TTF_CloseFont(hFont32);
		TTF_CloseFont(hFont48);
		TTF_CloseFont(hFont64);
		SDL_RWclose(rw_font);
	}

	SDL_FreePalette(program_palette);

	for(int i = 0; i < 3; i++) {
		SDL_FreeSurface(hBmpScreen[i]);
	}

	SDL_DestroyTexture(sdlTexture);]=]
  [=[	ags_pc98_font_unload();
	// フォント開放
	if (rw_font) {
		TTF_CloseFont(hFont16);
		TTF_CloseFont(hFont24);
		TTF_CloseFont(hFont32);
		TTF_CloseFont(hFont48);
		TTF_CloseFont(hFont64);
		SDL_RWclose(rw_font);
	}
	TTF_Quit();

	SDL_FreePalette(program_palette);

	for(int i = 0; i < 3; i++) {
		SDL_FreeSurface(hBmpScreen[i]);
	}

	if (scanline_texture) {
		SDL_DestroyTexture(scanline_texture);
		scanline_texture = NULL;
	}
	if (sdlTexture) {
		SDL_DestroyTexture(sdlTexture);
		sdlTexture = NULL;
	}]=]
  "AGS::~AGS destroy scanline_texture and TTF_Quit")
alicehub_patch_replace(patched_ags_cpp
  [=[void AGS::update_screen()
{
	if (!SDL_RectEmpty(&dirty_rect)) {
		SDL_Surface *sf;
		SDL_LockTextureToSurface(sdlTexture, &dirty_rect, &sf);
		SDL_BlitSurface(hBmpScreen[0], &dirty_rect, sf, NULL);
		SDL_UnlockTexture(sdlTexture);
		dirty_rect = {};
	}]=]
  [=[void AGS::update_screen()
{
	int tex_w = 0, tex_h = 0;
	if (!sdlTexture || SDL_QueryTexture(sdlTexture, NULL, NULL, &tex_w, &tex_h) != 0 ||
	    tex_w != screen_width || tex_h != screen_height) {
		if (sdlTexture)
			SDL_DestroyTexture(sdlTexture);
		SDL_SetWindowSize(g_window, window_width, window_height);
		SDL_RenderSetLogicalSize(g_renderer, window_width, window_height);
		sdlTexture = SDL_CreateTexture(g_renderer, SDL_PIXELFORMAT_ARGB8888, SDL_TEXTUREACCESS_STREAMING, screen_width, screen_height);
		if (scanline_texture) {
			SDL_DestroyTexture(scanline_texture);
			scanline_texture = create_scanline_texture(g_renderer, screen_width, screen_height);
		}
		dirty_rect = {0, 0, screen_width, screen_height};
	}

	if (!SDL_RectEmpty(&dirty_rect)) {
		if (!sdlTexture)
			return;
		SDL_Surface *sf = NULL;
		if (SDL_LockTextureToSurface(sdlTexture, &dirty_rect, &sf) != 0 || !sf)
			return;
		SDL_BlitSurface(hBmpScreen[0], &dirty_rect, sf, NULL);
		SDL_UnlockTexture(sdlTexture);
		dirty_rect = {};
	}]=]
  "AGS::update_screen texture resize and lock check")

# --- Patch sys/nact_input.cpp (common state reset; Switch patches layer on top below) ---
set(orig_nact_input "${src}/sys/nact_input.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_nact_input}")
file(READ "${orig_nact_input}" patched_nact_input)
alicehub_patch_replace(patched_nact_input
  [=[static int mousex, mousey, wheel;
static TouchState touch_state = TOUCH_NONE;]=]
  [=[static int mousex, mousey, wheel;
static TouchState touch_state = TOUCH_NONE;

void system3_reset_input_state()
{
	mousex = 0;
	mousey = 0;
	wheel = 0;
	touch_state = TOUCH_NONE;
}]=]
  "nact_input system3_reset_input_state")

if(NOT NINTENDO_SWITCH)
  file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/ags.cpp" CONTENT "${patched_ags_cpp}" @ONLY)
  list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/ags.cpp")
  file(CONFIGURE OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/nact_input.cpp" CONTENT "${patched_nact_input}" @ONLY)
  list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact_input.cpp")
endif()

if(ANDROID)
  add_library(system3 SHARED)
  target_link_options(system3 PRIVATE "LINKER:-z,max-page-size=16384" "LINKER:--exclude-libs,ALL")
  set(android_nact "${src}/android/nact_android.cpp")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${android_nact}")
  file(READ "${android_nact}" android_nact_source)
  set(original [=[bool NACT::handle_platform_event(const SDL_Event& e)
{
	return false;
}]=])
  set(replacement [=[#include "ags.h"

bool NACT::handle_platform_event(const SDL_Event& e)
{
	if (e.type == SDL_APP_DIDENTERFOREGROUND || e.type == SDL_RENDER_DEVICE_RESET) {
		ags->invalidate_screen(0, 0, ags->screen_width, ags->screen_height);
		return true;
	}
	return false;
}]=])
  string(REPLACE "${original}" "${replacement}" patched_android_nact "${android_nact_source}")
  if(patched_android_nact STREQUAL android_nact_source)
    message(FATAL_ERROR "Could not apply System 3 Android redraw patch")
  endif()
  file(CONFIGURE OUTPUT nact_android.cpp CONTENT "${patched_android_nact}" @ONLY)
  target_include_directories(system3 PRIVATE "${src}/android")
  target_sources(system3 PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/nact_android.cpp")
  target_link_libraries(system3 PRIVATE log)
elseif(ALICEHUB_IOS)
  enable_language(OBJC OBJCXX)
  add_library(system3 STATIC)
  target_compile_definitions(system3 PRIVATE SDL_MAIN_HANDLED ALICEHUB_IOS)
  set(orig_sdlmain "${src}/sdlmain.cpp")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_sdlmain}")
  file(READ "${orig_sdlmain}" ios_main)
  alicehub_patch_replace(ios_main
    [=[int main(int argc, char *argv[])
{]=]
    [=[int main(int argc, char *argv[])
{
	system3_reset_input_state();
	try {]=]
    "System 3 iOS try wrapper begin")
  alicehub_patch_replace(ios_main
    [=[	SDL_DestroyRenderer(g_renderer);
	SDL_DestroyWindow(g_window);
	SDL_Quit();

#ifdef __SWITCH__
	romfsExit();
#endif

    return exit_code;
}]=]
    [=[	if (g_renderer) {
		SDL_DestroyRenderer(g_renderer);
		g_renderer = nullptr;
	}
	if (g_window) {
		SDL_DestroyWindow(g_window);
		g_window = nullptr;
	}
	SDL_Quit();
	system3_reset_input_state();
	return exit_code;
	} catch (const System3IosError&) {
		g_nact.reset();
		if (g_renderer) {
			SDL_DestroyRenderer(g_renderer);
			g_renderer = nullptr;
		}
		if (g_window) {
			SDL_DestroyWindow(g_window);
			g_window = nullptr;
		}
		SDL_Quit();
		system3_reset_input_state();
		return 1;
	}
}]=]
    "System 3 iOS catch wrapper & teardown")
  alicehub_patch_replace(ios_main
    [=[	SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "system3", buf, g_window);
	exit(1);]=]
    [=[	SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, "system3", buf, g_window);
	throw System3IosError{};]=]
    "System 3 iOS sys_error exception throw")
  set(sdlmain_source "${CMAKE_CURRENT_BINARY_DIR}/sdlmain.cpp")
  file(CONFIGURE OUTPUT "${sdlmain_source}" CONTENT "namespace { struct System3IosError {}; }\nvoid system3_reset_input_state();\n${ios_main}" @ONLY)
  set_source_files_properties("${sdlmain_source}" PROPERTIES
    COMPILE_DEFINITIONS "main=system3_entry;SDL_CreateWindow=alicehub_system3_create_window;SDL_CreateRenderer=alicehub_system3_create_renderer")
  target_sources(system3 PRIVATE nact.ios.mm window.ios.m)
  set_target_properties(system3 PROPERTIES OBJCXX_STANDARD 17 OBJCXX_STANDARD_REQUIRED ON)
  set_source_files_properties(nact.ios.mm window.ios.m PROPERTIES COMPILE_OPTIONS -fobjc-arc)
elseif(NINTENDO_SWITCH)
  add_executable(system3 "${src}/generic/nact_generic.cpp")
  set(switch_input "${patched_nact_input}")
  set(quit_dialog "case SDL_QUIT:\n\t\tshow_quit_dialog();")
  string(FIND "${switch_input}" "${quit_dialog}" quit_offset)
  if(quit_offset EQUAL -1)
    message(FATAL_ERROR "Could not apply System 3 Switch quit patch")
  endif()
  string(REPLACE "${quit_dialog}" "case SDL_QUIT:\n\t\tquit(0);" switch_input "${switch_input}")
  foreach(button A B)
    set(call "SDL_GameControllerGetButton(sdl_gamecontroller, SDL_CONTROLLER_BUTTON_${button})")
    string(FIND "${switch_input}" "${call}" button_offset)
    if(button_offset EQUAL -1)
      message(FATAL_ERROR "Could not apply System 3 Switch button ${button} patch")
    endif()
    string(REPLACE "${call}"
      "SDL_GameControllerGetButton(sdl_gamecontroller, alicehub_switch_face_button(SDL_JoystickInstanceID(SDL_GameControllerGetJoystick(sdl_gamecontroller)), SDL_CONTROLLER_BUTTON_${button}))"
      switch_input "${switch_input}")
  endforeach()
  set(touch_dispatch "void NACT::handle_event(SDL_Event e)\n{")
  set(controller_poll "\tif(sdl_gamecontroller) {")
  set(return_keys "\treturn val;\n}\n\nvoid NACT::wait_key_release")
  set(finger_count "SDL_GetNumTouchFingers(e.tfinger.touchId)")
  foreach(fragment touch_dispatch controller_poll return_keys finger_count)
    string(FIND "${switch_input}" "${${fragment}}" offset)
    if(offset EQUAL -1)
      message(FATAL_ERROR "Could not apply System 3 Switch profile patch: ${fragment}")
    endif()
  endforeach()
  string(REPLACE "${touch_dispatch}" "${touch_dispatch}\n\tif (switch_controls_touch(&e)) {\n\t\tags->invalidate_screen(0, 0, ags->screen_width, ags->screen_height);\n\t\treturn;\n\t}" switch_input "${switch_input}")
  string(REPLACE "${finger_count}" "switch_controls_game_fingers(&e.tfinger)" switch_input "${switch_input}")
  string(REPLACE "${controller_poll}" "\tbool use_profile = switch_controls_remapping();\n${controller_poll}" switch_input "${switch_input}")
  string(REPLACE "if(SDL_GameControllerGetButton(" "if(!use_profile && SDL_GameControllerGetButton(" switch_input "${switch_input}")
  string(REPLACE "if(!use_profile && SDL_GameControllerGetButton(sdl_gamecontroller, SDL_CONTROLLER_BUTTON_DPAD_" "if((!use_profile && SDL_GameControllerGetButton(sdl_gamecontroller, SDL_CONTROLLER_BUTTON_DPAD_" switch_input "${switch_input}")
  string(REPLACE ") || SDL_GameControllerGetAxis" ")) || SDL_GameControllerGetAxis" switch_input "${switch_input}")
  string(REPLACE "${return_keys}" "\tif (use_profile)\n\t\tval |= switch_controls_buttons(switch_controls_gamecontroller(sdl_gamecontroller)) & 0xff;\n${return_keys}" switch_input "${switch_input}")
  set(input_pump "void NACT::pump_events()\n{\n")
  string(FIND "${switch_input}" "${input_pump}" pump_offset)
  if(pump_offset EQUAL -1)
    message(FATAL_ERROR "Could not apply System 3 Switch runtime menu patch")
  endif()
  string(REPLACE "${input_pump}"
    "${input_pump}\tif (switch_menu_system3_poll(ags, msgskip)) {\n\t\ttouch_state = TOUCH_NONE;\n\t\twheel = 0;\n\t}\n"
    switch_input "${switch_input}")
  file(CONFIGURE OUTPUT nact_input.cpp CONTENT "#include \"controller.h\"\n#include \"hub/controller_runtime.h\"\nclass AGS;\nclass MsgSkip;\nbool switch_menu_system3_poll(AGS *, MsgSkip *);\n${switch_input}" @ONLY)
  list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact_input.cpp")
  set(switch_ags "${patched_ags_cpp}")
  set(present "\tSDL_RenderPresent(g_renderer);")
  string(FIND "${switch_ags}" "${present}" offset)
  if(offset EQUAL -1)
    message(FATAL_ERROR "Could not apply System 3 Switch overlay draw patch")
  endif()
  string(REPLACE "${present}" "\tswitch_controls_draw_sdl(g_renderer);\n${present}" switch_ags "${switch_ags}")
  file(CONFIGURE OUTPUT ags.cpp CONTENT "#include \"hub/controller_runtime.h\"\n${switch_ags}" @ONLY)
  list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/ags.cpp")
  set(sdlmain "${src}/sdlmain.cpp")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${sdlmain}")
  file(READ "${sdlmain}" switch_main)
  set(startup_begin "\tg_window = create_window(config, game_id);")
  set(startup_end "\n\tg_nact.reset(NACT::create(config, game_id));\n")
  set(fatal_dialog "\tSDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, \"system3\", buf, g_window);")
  foreach(fragment startup_begin startup_end fatal_dialog)
    string(FIND "${switch_main}" "${${fragment}}" offset)
    if(offset EQUAL -1)
      message(FATAL_ERROR "Could not apply System 3 Switch startup patch: ${fragment}")
    endif()
  endforeach()
  string(REPLACE "${startup_begin}" "\tswitch_starting = true;\n\ttry {\n${startup_begin}" switch_main "${switch_main}")
  string(REPLACE "${startup_end}" "${startup_end}\n\t} catch (const SwitchStartupError&) {\n\t\tswitch_starting = false;\n\t\tSDL_DestroyRenderer(g_renderer);\n\t\tSDL_DestroyWindow(g_window);\n\t\tSDL_Quit();\n\t\tg_renderer = nullptr;\n\t\tg_window = nullptr;\n\t\tromfsExit();\n\t\treturn 1;\n\t}\n\tswitch_starting = false;" switch_main "${switch_main}")
  string(REPLACE "${fatal_dialog}" "\tif (switch_starting) {\n\t\talicehub_switch_set_launch_error(buf);\n\t\tthrow SwitchStartupError{};\n\t}\n${fatal_dialog}" switch_main "${switch_main}")
  set(sdlmain_source "${CMAKE_CURRENT_BINARY_DIR}/sdlmain.cpp")
  file(CONFIGURE OUTPUT "${sdlmain_source}" CONTENT "#include \"launcher.h\"\nnamespace { struct SwitchStartupError {}; bool switch_starting; }\n${switch_main}" @ONLY)
  set_source_files_properties("${sdlmain_source}" PROPERTIES
    COMPILE_DEFINITIONS "SDL_MAIN_HANDLED;main=system3_switch_entry")
  include(../switch/Packaging.cmake)
  set(romfs "${CMAKE_CURRENT_BINARY_DIR}/romfs")
  file(COPY "${system3_source_SOURCE_DIR}/resources/mda"
            "${system3_source_SOURCE_DIR}/resources/fonts" DESTINATION "${romfs}")
  alicehub_switch_package(system3 "${romfs}")
  target_sources(system3 PRIVATE ../switch/hub/runtime_menu_system3.cpp)
else()
  add_executable(system3 "${src}/generic/nact_generic.cpp")
endif()

if(NOT DEFINED sdlmain_source)
  set(sdlmain_source "${src}/sdlmain.cpp")
endif()
target_sources(system3 PRIVATE
  platform.cpp
  "${sdlmain_source}"
  "${src}/config.cpp"
  "${src}/fileio.cpp"
  "${CMAKE_CURRENT_BINARY_DIR}/encoding.cpp"
  "${src}/texthook.cpp"
  "${CMAKE_CURRENT_BINARY_DIR}/fm/makofm.cpp"
  "${CMAKE_CURRENT_BINARY_DIR}/fm/mako_ymfm.cpp"
  "${CMAKE_CURRENT_BINARY_DIR}/mako.cpp"
  "${ymfm_SOURCE_DIR}/src/ymfm_opn.cpp"
  "${ymfm_SOURCE_DIR}/src/ymfm_adpcm.cpp"
  "${ymfm_SOURCE_DIR}/src/ymfm_ssg.cpp"
  "${sdl2_ttf_SOURCE_DIR}/SDL_ttf.c"
  ${vm_sources})
target_include_directories(system3 PRIVATE "${CMAKE_CURRENT_BINARY_DIR}" "${src}" "${src}/sys"
  "${ymfm_SOURCE_DIR}/src" "${sdl2_ttf_SOURCE_DIR}"
  "${system3_source_SOURCE_DIR}/deps/dr_libs" "${system3_source_SOURCE_DIR}/deps/stb")
target_compile_features(system3 PRIVATE cxx_std_17)
target_link_libraries(system3 PRIVATE SDL2::SDL2 Freetype::Freetype)
set_target_properties(system3 PROPERTIES C_VISIBILITY_PRESET hidden CXX_VISIBILITY_PRESET hidden
  VISIBILITY_INLINES_HIDDEN ON)

install(TARGETS system3)
install(DIRECTORY "${system3_source_SOURCE_DIR}/resources/mda" DESTINATION share/system3)
install(FILES "${system3_source_SOURCE_DIR}/COPYING.txt" DESTINATION share/system3/licenses RENAME system3-sdl2)
install(FILES "${ymfm_SOURCE_DIR}/LICENSE" DESTINATION share/system3/licenses RENAME ymfm)
install(FILES "${sdl2_ttf_SOURCE_DIR}/LICENSE.txt" DESTINATION share/system3/licenses RENAME SDL2_ttf)
install(FILES "${system3_source_SOURCE_DIR}/deps/dr_libs/dr_mp3.h" DESTINATION share/system3/licenses RENAME dr_mp3)
install(FILES "${system3_source_SOURCE_DIR}/deps/stb/stb_vorbis.h" DESTINATION share/system3/licenses RENAME stb_vorbis)
