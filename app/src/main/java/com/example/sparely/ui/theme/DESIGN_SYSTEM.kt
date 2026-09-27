package com.example.sparely.ui.theme

/**
 * DESIGN SYSTEM & COLOR CONSISTENCY GUIDELINES FOR SPARELY
 *
 * This file documents the centralized color and icon system to ensure consistency
 * across the entire app. All developers should follow these guidelines when implementing UI.
 *
 * ================================================================================
 * COLOR SYSTEM OVERVIEW
 * ================================================================================
 *
 * The app uses Material 3 Dynamic Color system which adapts to the user's device theme.
 * All colors should be sourced from MaterialTheme.colorScheme to support:
 * - Automatic theming on Android 12+
 * - Consistent dark mode support
 * - User accessibility preferences
 *
 * PRIMARY COLOR SCHEME:
 * - primary: Main action color (buttons, highlights)
 * - onPrimary: Text/icons on primary backgrounds
 * - primaryContainer: Subtle background for primary elements
 * - onPrimaryContainer: Text on primary containers
 *
 * SECONDARY COLOR SCHEME:
 * - secondary: Alternative action color for secondary actions
 * - onSecondary: Text/icons on secondary backgrounds
 * - secondaryContainer: Subtle background for secondary elements
 * - onSecondaryContainer: Text on secondary containers
 *
 * TERTIARY COLOR SCHEME:
 * - tertiary: Accent color for special emphasis
 * - onTertiary: Text/icons on tertiary backgrounds
 * - tertiaryContainer: Subtle background for tertiary elements
 * - onTertiaryContainer: Text on tertiary containers
 *
 * ERROR COLORS (Critical/Destructive):
 * - error: Red, for errors, deletions, warnings
 * - onError: Text/icons on error backgrounds
 * - errorContainer: Subtle background for error elements
 * - onErrorContainer: Text on error containers
 *
 * NEUTRAL COLORS:
 * - background: Page background
 * - onBackground: Text on background
 * - surface: Card, sheet backgrounds
 * - onSurface: Text on surfaces
 * - surfaceVariant: Secondary surface color
 * - onSurfaceVariant: Secondary text color
 * - outline: Borders, dividers
 *
 * SEMANTIC COLORS (from ExpressiveTokens):
 * - success: Green, for positive actions/status (tertiary in scheme)
 * - onSuccess: Text on success backgrounds
 * - successContainer: Subtle success background
 * - onSuccessContainer: Text on success containers
 *
 * - warning: Yellow/Orange, for warnings/caution (secondary in scheme)
 * - onWarning: Text on warning backgrounds
 * - warningContainer: Subtle warning background
 * - onWarningContainer: Text on warning containers
 *
 * - critical: Red, for errors/deletions (same as error)
 * - onCritical: Text on critical backgrounds
 * - criticalContainer: Subtle critical background
 * - onCriticalContainer: Text on critical containers
 *
 * ================================================================================
 * USAGE PATTERNS
 * ================================================================================
 *
 * 1. TEXT COLORS:
 *    - Primary text: use onBackground (dark text in light mode, light text in dark mode)
 *    - Secondary text: use onSurfaceVariant
 *    - Text on colored backgrounds: use on<Color> (e.g., onPrimary for text on primary)
 *
 * 2. ICON COLORS:
 *    - Primary icons: use primary
 *    - Secondary icons: use secondary
 *    - Action icons: use appropriate semantic color (add=success, delete=critical)
 *    - Category icons: use getCategoryColor(category)
 *    - Status icons: use getStatusColor(status)
 *
 * 3. BACKGROUND COLORS:
 *    - Primary container backgrounds: use primaryContainer with alpha
 *    - Category backgrounds: use categoryColorVariant(category, alpha = 0.1f)
 *    - Card backgrounds: use surface or surfaceContainerHigh
 *    - Dialog/Sheet backgrounds: use surface
 *
 * 4. BUTTON COLORS:
 *    - Primary action: use primary background with onPrimary text
 *    - Secondary action: use secondary background with onSecondary text
 *    - Outlined: use primary text with outline border, surface background
 *    - Delete/Destructive: use critical/error background with onError text
 *
 * ================================================================================
 * CATEGORY COLORS & ICONS
 * ================================================================================
 *
 * All expense categories have consistent color-icon pairs:
 *
 * GROCERIES:    🛒 SHOPPING_CART, success (green)
 * DINING:       🍽️  RESTAURANT, critical (red)
 * TRANSPORTATION: 🚗 DIRECTIONS_CAR, primary (blue)
 * ENTERTAINMENT: 🎉 CELEBRATION, tertiary (pink)
 * UTILITIES:    💡 LIGHTBULB, secondary (purple)
 * HEALTH:       🏥 HEALTH_AND_SAFETY, critical (red)
 * EDUCATION:    🎓 SCHOOL, tertiary (pink)
 * SHOPPING:     🛍️  SHOPPING_BAG, secondary (purple)
 * TRAVEL:       ✈️  FLIGHT, primary (blue)
 * OTHER:        📁 CATEGORY, outline (gray)
 *
 * Usage:
 *   color = getCategoryColor(category)
 *   icon = getCategoryIcon(category)
 *   backgroundColor = categoryColorVariant(category, 0.1f)
 *
 * ================================================================================
 * ICON ORGANIZATION
 * ================================================================================
 *
 * Icons are organized by semantic groups in IconUtils.kt:
 * - ActionIcons: ADD, DELETE, EDIT, SAVE, etc.
 * - StatusIcons: SUCCESS, ERROR, WARNING, INFO, etc.
 * - NavigationIcons: HOME, SETTINGS, HISTORY, etc.
 * - FinancialIcons: WALLET, TRANSFER, INCOME, EXPENSE, etc.
 * - TimeIcons: CALENDAR, CLOCK, TODAY, etc.
 * - CommunicationIcons: NOTIFICATION, EMAIL, COMMENT, etc.
 * - UtilityIcons: MENU, EXPAND, VISIBILITY, etc.
 *
 * Usage:
 *   MaterialSymbolIcon(icon = ActionIcons.DELETE, ...)
 *   MaterialSymbolIcon(icon = getStatusIcon(StatusType.ERROR), ...)
 *   MaterialSymbolIcon(icon = getCategoryIcon(ExpenseCategory.DINING), ...)
 *
 * ================================================================================
 * ANTI-PATTERNS (DON'T DO THIS!)
 * ================================================================================
 *
 * ❌ Don't use hardcoded colors:
 *    tint = Color(0xFFFF5722)  // WRONG!
 * ✅ Do use semantic colors:
 *    tint = MaterialTheme.colorScheme.critical
 *
 * ❌ Don't use mismatched icons for actions:
 *    delete icon with success color = CONFUSING!
 * ✅ Do use semantic icon-color pairs:
 *    MaterialSymbolIcon(ActionIcons.DELETE, tint = MaterialTheme.colorScheme.critical)
 *
 * ❌ Don't import icons directly in screens:
 *    import com.example.sparely.ui.theme.MaterialSymbols.ADD
 * ✅ Do use icon objects or functions:
 *    ActionIcons.ADD or getCategoryIcon(category)
 *
 * ❌ Don't create custom status colors:
 *    val customError = Color(0xFFE74C3C)
 * ✅ Do use semantic status colors:
 *    getStatusColor(StatusType.ERROR)
 *
 * ❌ Don't hardcode alpha values inconsistently:
 *    surface.copy(alpha = 0.05f)  // Different from 0.1f elsewhere
 * ✅ Do use consistent alpha values:
 *    categoryColorVariant(category, 0.1f)  // Standardized
 *
 * ================================================================================
 * ACCESSIBILITY CONSIDERATIONS
 * ================================================================================
 *
 * - Always use sufficient contrast: onBackground text on surface = 7:1 ratio
 * - Status indicators shouldn't rely on color alone: add icons too
 * - Use onSurfaceVariant for disabled/secondary content
 * - Test all colors in both light and dark modes
 * - Icons should work for colorblind users (don't use red-green only)
 *
 * ================================================================================
 * EXTENSION FUNCTIONS AVAILABLE
 * ================================================================================
 *
 * From SemanticColors.kt:
 * - ColorScheme.categoryColor(category) -> Color
 * - ColorScheme.categoryColorVariant(category, alpha) -> Color
 * - ColorScheme.onCategoryColor(category) -> Color
 * - getStatusColor(status) -> Color
 * - getActionColor(action) -> Color
 *
 * From CategoryUtils.kt (legacy):
 * - getCategoryColor(category) -> Color
 * - getCategoryIcon(category) -> Int
 *
 * From IconUtils.kt:
 * - getCategoryIcon(category) -> Int
 * - StatusIcons, ActionIcons, NavigationIcons, etc.
 *
 * ================================================================================
 * IMPLEMENTATION CHECKLIST
 * ================================================================================
 *
 * When implementing a new screen or component:
 *
 * [ ] All colors use MaterialTheme.colorScheme
 * [ ] Category colors use getCategoryColor()
 * [ ] Category icons use getCategoryIcon()
 * [ ] Delete/Error actions use critical/error color
 * [ ] Success/Add actions use success color
 * [ ] Icon colors match semantic meaning (not just style)
 * [ ] Text colors use appropriate on<Color> variants
 * [ ] Backgrounds use surface, primaryContainer, or categoryColorVariant
 * [ ] All icons use IconUtils objects or semantic functions
 * [ ] Dark mode appearance verified
 * [ ] Contrast ratios meet WCAG standards
 * [ ] No hardcoded Color(0x...) values
 *
 * ================================================================================
 */
