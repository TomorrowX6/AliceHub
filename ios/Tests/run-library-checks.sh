#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
archive="$(brew --prefix libarchive)"
mkdir -p ios/build/checks
xcrun clang -Wall -Wextra -Werror -I"$archive/include" -c ios/App/ArchiveExtract.c -o ios/build/checks/ArchiveExtract.o
xcrun swiftc -import-objc-header ios/App/ArchiveExtract.h \
  ios/App/GameLibrary.swift ios/App/LocalGameImport.swift ios/App/LocalImportCleanup.swift ios/App/GameDisplayScale.swift \
  ios/App/GamepadProfile.swift ios/App/HubLanguage.swift ios/App/HubLocalizedText.swift ios/App/HubToolFiles.swift ios/App/ArchiveExtractor.swift ios/App/Iso9660Extractor.swift \
  ios/App/UpdateCheck.swift \
  ios/Tests/LibraryChecks.swift ios/Tests/ArchiveChecks.swift ios/build/checks/ArchiveExtract.o \
  -L"$archive/lib" -larchive -liconv -o ios/build/checks/LibraryChecks
ios/build/checks/LibraryChecks
