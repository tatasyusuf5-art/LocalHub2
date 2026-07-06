import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# I removed background(Black).
# Let's restore it in the calculator Box.
target_calc_box = """            // Standard black background for calculator or when no image is set
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    
            )"""
replacement_calc_box = """            // Standard black background for calculator or when no image is set
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
            )"""

content = content.replace(target_calc_box, replacement_calc_box)

# What about the TopBar?
target_top_bar = """            } else {
                // Regular Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground)
                        .statusBarsPadding()
                        
                        .padding(horizontal = 8.dp, vertical = 8.dp),"""
replacement_top_bar = """            } else {
                // Regular Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground)
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),"""

content = content.replace(target_top_bar, replacement_top_bar)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
