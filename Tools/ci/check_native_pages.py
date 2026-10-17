"""CI artifact inspection: every packaged 64-bit ELF must support 16 KB pages."""
import struct
import sys
import zipfile


for artifact in sys.argv[1:]:
    count = 0
    with zipfile.ZipFile(artifact) as archive:
        for name in archive.namelist():
            if not name.endswith(".so") or not any(f"/lib/{abi}/" in f"/{name}"
                                                  for abi in ("arm64-v8a", "x86_64")):
                continue
            elf = archive.read(name)
            assert elf[:6] == b"\x7fELF\x02\x01", f"Invalid 64-bit ELF: {artifact}: {name}"
            offset = struct.unpack_from("<Q", elf, 32)[0]
            entry_size, entries = struct.unpack_from("<HH", elf, 54)
            for index in range(entries):
                kind, _, file_offset, address, _, _, _, alignment = struct.unpack_from(
                    "<IIQQQQQQ", elf, offset + index * entry_size)
                if kind == 1:
                    assert alignment >= 16384 and (address - file_offset) % 16384 == 0, (
                        f"Not 16 KB compatible: {artifact}: {name}, LOAD alignment={alignment}")
            count += 1
    assert count > 0, f"No 64-bit native libraries packaged: {artifact}"
    print(f"{artifact}: {count} native libraries support 16 KB pages")
