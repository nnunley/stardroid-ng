#!/usr/bin/env python3
"""Generate C header with embedded SPIR-V shader bytecode."""

import sys
import os

def spv_to_c_array(spv_path, array_name):
    """Convert SPIR-V file to C array declaration."""
    with open(spv_path, 'rb') as f:
        data = f.read()

    # Format as C array with alignment for Vulkan SPIR-V (must be uint32_t aligned)
    lines = [f"alignas(4) unsigned char {array_name}[] = {{"]

    # Write 12 bytes per line
    for i in range(0, len(data), 12):
        chunk = data[i:i+12]
        hex_values = ", ".join(f"0x{b:02x}" for b in chunk)
        if i + 12 < len(data):
            hex_values += ","
        lines.append(f"  {hex_values}")

    lines.append("};")
    lines.append(f"unsigned int {array_name}_len = {len(data)};")

    return "\n".join(lines)

def main():
    if len(sys.argv) < 4:
        print(f"Usage: {sys.argv[0]} <output.h> <vert.spv> <frag.spv>")
        sys.exit(1)

    output_path = sys.argv[1]
    vert_spv = sys.argv[2]
    frag_spv = sys.argv[3]

    header = [
        "// Auto-generated shader header - do not edit",
        "#pragma once",
        "#include <cstdint>",
        "",
    ]

    header.append(spv_to_c_array(vert_spv, "triangle_vert_spv"))
    header.append("")
    header.append(spv_to_c_array(frag_spv, "triangle_frag_spv"))
    header.append("")

    with open(output_path, 'w') as f:
        f.write("\n".join(header))

    print(f"Generated {output_path}")

if __name__ == "__main__":
    main()
