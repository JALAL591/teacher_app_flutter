#!/usr/bin/env python3
"""
سكريبت آمن لإضافة وسم <layout> لملفات XML
مع حذف xmlns المكررة من العناصر الداخلية
"""

import os
import re
import sys
import shutil
from pathlib import Path

def add_layout_wrapper(xml_content: str, filename: str) -> str:
    """إضافة وسم layout مع الحفاظ على السمات"""
    
    # تجاهل الملفات التي تحتوي على layout بالفعل
    if '<layout' in xml_content:
        print(f"  ⏭️  تم تجاهل (موجود): {filename}")
        return xml_content
    
    # استخراج سطر XML declaration
    xml_declaration = ""
    content = xml_content
    
    if xml_content.startswith('<?xml'):
        end_decl = xml_content.index('?>') + 2
        xml_declaration = xml_content[:end_decl].strip()
        content = xml_content[end_decl:].strip()
    
    # استخراج جميع xmlns من العنصر الجذري
    xmlns_pattern = re.compile(r'\s+xmlns:[a-zA-Z_]+="[^"]*"')
    xmlns_matches = xmlns_pattern.findall(content)
    
    # بناء وسم layout مع xmlns
    layout_xmlns = ""
    for xmlns in xmlns_matches:
        layout_xmlns += xmlns
    
    # إذا لم يوجد xmlns:android أضفه
    if 'xmlns:android' not in layout_xmlns:
        layout_xmlns = '\n    xmlns:android="http://schemas.android.com/apk/res/android"' + layout_xmlns
    
    # إذا لم يوجد xmlns:app أضفه
    if 'xmlns:app' not in layout_xmlns:
        layout_xmlns += '\n    xmlns:app="http://schemas.android.com/apk/res-auto"'
    
    # حذف xmlns من العنصر الجذري الداخلي
    # (لأنها ستنتقل لوسم layout)
    content_cleaned = xmlns_pattern.sub('', content)
    
    # التأكد من أن العنصر الجذري لا يزال صحيحاً
    # (لم تُحذف layout_width أو layout_height)
    if 'layout_width' not in content_cleaned:
        print(f"  ⚠️  تحذير: layout_width مفقود في {filename}")
        return xml_content  # إرجاع الأصل بدون تعديل
    
    # بناء الملف النهائي
    result = ""
    if xml_declaration:
        result += xml_declaration + "\n"
    
    result += f'<layout{layout_xmlns}>\n\n'
    result += '    <data>\n    </data>\n\n'
    
    # إضافة المحتوى مع indent
    for line in content_cleaned.split('\n'):
        if line.strip():
            result += '    ' + line + '\n'
        else:
            result += '\n'
    
    result += '\n</layout>'
    
    return result


def process_directory(layout_dir: str, backup: bool = True):
    """معالجة جميع ملفات XML في المجلد"""
    
    layout_path = Path(layout_dir)
    
    if not layout_path.exists():
        print(f"❌ المجلد غير موجود: {layout_dir}")
        sys.exit(1)
    
    # إنشاء نسخة احتياطية
    if backup:
        backup_dir = str(layout_path) + "_backup"
        if not os.path.exists(backup_dir):
            shutil.copytree(layout_dir, backup_dir)
            print(f"✅ نسخة احتياطية في: {backup_dir}")
    
    xml_files = list(layout_path.glob("*.xml"))
    print(f"\n📁 عدد الملفات: {len(xml_files)}\n")
    
    success = 0
    skipped = 0
    errors = 0
    
    for xml_file in xml_files:
        try:
            # قراءة الملف
            with open(xml_file, 'r', encoding='utf-8') as f:
                original = f.read()
            
            # تطبيق التعديل
            modified = add_layout_wrapper(original, xml_file.name)
            
            if modified == original:
                skipped += 1
                continue
            
            # التحقق من صحة XML الناتج
            if '<layout' not in modified:
                print(f"  ❌ فشل: {xml_file.name}")
                errors += 1
                continue
            
            # كتابة الملف
            with open(xml_file, 'w', encoding='utf-8') as f:
                f.write(modified)
            
            print(f"  ✅ تم: {xml_file.name}")
            success += 1
            
        except Exception as e:
            print(f"  ❌ خطأ في {xml_file.name}: {e}")
            errors += 1
    
    print(f"\n{'='*40}")
    print(f"✅ نجح:    {success}")
    print(f"⏭️  تجاهل:  {skipped}")
    print(f"❌ أخطاء:  {errors}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("الاستخدام: python3 fix_xml_layout.py <مسار مجلد layout>")
        print("مثال: python3 fix_xml_layout.py app/src/main/res/layout")
        sys.exit(1)
    
    layout_directory = sys.argv[1]
    process_directory(layout_directory)