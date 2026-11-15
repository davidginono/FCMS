#!/usr/bin/env python3
"""Compare isolated original PHP presentation fixtures with the Spring renderer.

Requires PyMuPDF and numpy. First run OperationalReportPdfTests and the isolated
reference-operational-exports.php harness for TASK and EXPENSE (see documentation).
"""
import argparse
import json
from pathlib import Path
import fitz
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument('--directory', default='.verification/operational-exports')
parser.add_argument('--cases', nargs='+', default=['TASK','EXPENSE'])
args = parser.parse_args()
root = Path(args.directory)
report = {}
for kind in args.cases:
    with fitz.open(root / ('original-' + kind + '.pdf')) as original, fitz.open(root / ('spring-original-' + kind + '.pdf')) as spring:
        assert len(original) == len(spring) == 1
        def spans(page):
            return [(s['text'], s['font'], s['size'], s['origin'])
                    for block in page.get_text('dict')['blocks'] if 'lines' in block
                    for line in block['lines'] for s in line['spans']]
        source, current = spans(original[0]), spans(spring[0])
        # The old expense script placed its footer at Y=0, overwriting the title.
        # Its corrected bottom footer is an explicit adaptation outside this body comparison.
        if kind == 'EXPENSE':
            current = current[:len(source)]
        deltas = [(a, b) for a, b in zip(source, current) if a != b]
        assert len(source) == len(current) and not deltas, (kind, deltas)
        a, b = [page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False) for page in [original[0], spring[0]]]
        x = np.frombuffer(a.samples, dtype=np.uint8).reshape(a.height, a.width, 3)
        y = np.frombuffer(b.samples, dtype=np.uint8).reshape(b.height, b.width, 3)
        if kind == 'EXPENSE':
            x, y = x[:-85], y[:-85]
        equal = np.array_equal(x, y)
        report[kind] = {'pages': 1, 'text_font_position_deltas': len(deltas), 'pixels_equal': equal,
                        'pixel_equality_percent': 100 * (1 - np.any(x != y, axis=2).mean()),
                        'footer_correction_excluded': kind == 'EXPENSE'}
        assert equal, report[kind]
(root / 'comparison.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
