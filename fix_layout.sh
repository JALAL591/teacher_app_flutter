#!/bin/bash
cd /storage/internal_new/project/teacher_app_flutter-main/app/src/main/res/layout

for f in activity_*.xml bottom_*.xml dialog_*.xml item_*.xml view_stat_item.xml; do
  if [ -f "$f" ]; then
    # Create temp file with layout wrapper
    {
      head -1 "$f"
      echo "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:app=\"http://schemas.android.com/apk/res-auto\">"
      tail -n +2 "$f" | sed 's/ xmlns:android="http://schemas.android.com/apk/res/android"//g' | sed 's/ xmlns:app="http://schemas.android.com/apk/res-auto"//g' | sed 's/ xmlns:tools="http://schemas.android.com/tools"//g'
      echo "</layout>"
    } > "$f.tmp"
    mv "$f.tmp" "$f"
  fi
done
echo "Done"