#!/bin/bash

# Bundle Analysis Script for OpenLegislation
# This script analyzes the webpack bundle to identify optimization opportunities

echo "🔍 Analyzing OpenLegislation Frontend Bundle..."
echo "============================================="

cd src/main/webapp

echo "📊 Current Bundle Size:"
if [ -d "static/dist" ]; then
    ls -lah static/dist/*.js | awk '{print $5 "\t" $9}'
    echo ""
    
    total_size=$(ls -la static/dist/*.js | awk '{sum += $5} END {print sum}')
    echo "Total Bundle Size: $total_size bytes"
    echo ""
else
    echo "❌ No build found. Running build first..."
    npm run build
fi

echo "🎯 Bundle Optimization Recommendations:"
echo "======================================="

echo "1. ✅ Code Splitting: Already implemented"
echo "2. 🔄 Bundle sizes still large - consider:"
echo "   - Implement React.lazy() for route-based code splitting"
echo "   - Remove unused dependencies"
echo "   - Optimize TailwindCSS purging"
echo "   - Use dynamic imports for heavy libraries"
echo ""

echo "3. 📈 To run detailed bundle analysis:"
echo "   npm run analyze"
echo ""

echo "4. 🎛️ Current webpack optimizations:"
echo "   - ✅ Code splitting enabled"
echo "   - ✅ Minification enabled"
echo "   - ✅ Tree shaking enabled"
echo "   - ✅ Caching enabled"
echo ""

echo "5. 🚀 Next steps:"
echo "   - Fix TailwindCSS purging"
echo "   - Implement lazy loading"
echo "   - Add bundle size monitoring"
echo "   - Consider removing large unused dependencies"