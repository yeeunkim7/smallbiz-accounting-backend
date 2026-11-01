import React, { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { money } from './format';
export const color = { bg: '#F5F7F8', white: '#FFFFFF', text: '#172B2A', muted: '#536B67', border: '#DDE5E3', primary: '#086F63', pale: '#E6F2EE', attention: '#9A4210', error: '#B42318' };
export function Icon({ name, size = 21, tint = color.primary }: { name: React.ComponentProps<typeof Ionicons>['name']; size?: number; tint?: string }) { return <Ionicons name={name} size={size} color={tint} accessible={false} />; }
export function Button({ title, onPress, secondary = false, quiet = false, disabled = false, busy = false, icon }: { title: string; onPress(): void; secondary?: boolean; quiet?: boolean; disabled?: boolean; busy?: boolean; icon?: React.ComponentProps<typeof Ionicons>['name'] }) {
  const [focused, setFocused] = useState(false);
  return <Pressable accessibilityRole="button" accessibilityLabel={title} accessibilityState={{ disabled: disabled || busy, busy }} disabled={disabled || busy} onPress={onPress} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} style={({ pressed }) => [s.button, secondary && s.secondary, quiet && s.quiet, (disabled || busy) && { opacity: .5 }, pressed && { opacity: .75 }, focused && s.focus]}>
    {busy ? <ActivityIndicator color={secondary || quiet ? color.primary : color.white} /> : icon ? <Icon name={icon} tint={secondary || quiet ? color.primary : color.white} size={19} /> : null}<Text style={[s.buttonText, (secondary || quiet) && { color: color.primary }]}>{title}</Text>
  </Pressable>;
}
export function Field({ label, value, onChange, hint, secret, numeric, multiline, maxLength, autoComplete, disabled = false }: { label: string; value: string; onChange(v: string): void; hint?: string; secret?: boolean; numeric?: boolean; multiline?: boolean; maxLength?: number; autoComplete?: React.ComponentProps<typeof TextInput>['autoComplete']; disabled?: boolean }) {
  const [focused, setFocused] = useState(false);
  return <View style={s.field}><Text style={s.label}>{label}</Text><TextInput editable={!disabled} accessibilityLabel={label} value={value} onChangeText={onChange} secureTextEntry={secret} keyboardType={numeric ? 'number-pad' : 'default'} autoCapitalize="none" autoCorrect={false} autoComplete={autoComplete ?? 'off'} multiline={multiline} maxLength={maxLength} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} style={[s.input, multiline && { minHeight: 96, textAlignVertical: 'top' }, focused && s.inputFocus]} placeholderTextColor={color.muted} />{hint ? <Text style={s.hint}>{hint}</Text> : null}</View>;
}
export function Notice({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <View accessibilityLiveRegion="polite" style={[s.notice, error && s.errorNotice]}><Icon name={error ? 'alert-circle-outline' : 'information-circle-outline'} tint={error ? color.error : color.primary} /><Text style={[s.noticeText, error && { color: color.error }]}>{children}</Text></View>; }
export function Loading() { return <View style={s.loading} accessibilityLiveRegion="polite"><ActivityIndicator color={color.primary} /><Text style={s.muted}>자료를 불러오고 있어요</Text></View>; }
export function QueryError({ text, retry }: { text: string; retry(): void }) { return <View style={s.gap}><Notice error>{text}</Notice><Button title="다시 불러오기" onPress={retry} secondary /></View>; }
export function Heading({ eyebrow, title, subtitle }: { eyebrow?: string; title: string; subtitle?: string }) { return <View style={s.heading}>{eyebrow ? <Text style={s.eyebrow}>{eyebrow}</Text> : null}<Text accessibilityRole="header" style={s.title}>{title}</Text>{subtitle ? <Text style={s.subtitle}>{subtitle}</Text> : null}</View>; }
export function Empty({ title, text, action }: { title: string; text: string; action?: React.ReactNode }) { return <View style={s.empty}><View style={s.emptyIcon}><Icon name="receipt-outline" size={28} /></View><Text style={s.sectionTitle}>{title}</Text><Text style={[s.body, { textAlign: 'center' }]}>{text}</Text>{action}</View>; }
export const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: color.bg }, scroll: { flexGrow: 1, width: '100%', maxWidth: 960, alignSelf: 'center', paddingHorizontal: 24, paddingTop: 28, paddingBottom: 40, gap: 28 },
  header: { borderBottomWidth: 1, borderColor: color.border, backgroundColor: color.white }, headerInner: { maxWidth: 960, width: '100%', alignSelf: 'center', paddingHorizontal: 24, minHeight: 76, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 }, brand: { flexDirection: 'row', alignItems: 'center', gap: 10, flexShrink: 1 }, logo: { width: 36, height: 36, borderRadius: 11, backgroundColor: color.primary, alignItems: 'center', justifyContent: 'center' }, brandText: { color: color.text, fontSize: 19, fontWeight: '800', letterSpacing: -.8 }, context: { fontSize: 12, color: color.muted, marginTop: 3 },
  heading: { gap: 8 }, eyebrow: { color: color.primary, fontSize: 12, fontWeight: '700', letterSpacing: 1.3 }, title: { color: color.text, fontSize: 30, fontWeight: '800', letterSpacing: -1.1, lineHeight: 39 }, subtitle: { color: color.muted, fontSize: 15, lineHeight: 24 }, body: { color: color.muted, fontSize: 15, lineHeight: 24 }, muted: { color: color.muted, fontSize: 13, lineHeight: 21 }, label: { color: color.text, fontSize: 14, fontWeight: '600', marginBottom: 8 },
  sectionTitle: { color: color.text, fontSize: 19, fontWeight: '700', letterSpacing: -.4 }, sectionRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }, gap: { gap: 16 }, row: { flexDirection: 'row', alignItems: 'center', gap: 12, flexWrap: 'wrap' }, grow: { flex: 1 },
  button: { minHeight: 48, paddingVertical: 12, paddingHorizontal: 18, borderRadius: 12, backgroundColor: color.primary, flexDirection: 'row', gap: 8, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: 'transparent' }, secondary: { backgroundColor: color.white, borderColor: color.border }, quiet: { backgroundColor: 'transparent', borderColor: 'transparent', paddingHorizontal: 10 }, buttonText: { fontSize: 15, fontWeight: '700', color: color.white, flexShrink: 1 }, focus: { borderColor: '#9A4210' },
  field: { width: '100%' }, input: { minHeight: 50, borderWidth: 1, borderColor: '#A9BBB5', borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16, color: color.text, backgroundColor: color.white }, inputFocus: { borderWidth: 2, borderColor: color.primary, paddingHorizontal: 13, paddingVertical: 11 }, hint: { color: color.muted, fontSize: 12, lineHeight: 19, marginTop: 6 },
  notice: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, backgroundColor: color.pale, borderRadius: 12, padding: 16 }, noticeText: { flex: 1, fontSize: 14, lineHeight: 22, color: color.primary }, errorNotice: { backgroundColor: '#FFF0EB' }, loading: { minHeight: 140, alignItems: 'center', justifyContent: 'center', gap: 12 },
  surface: { backgroundColor: color.white, borderRadius: 18, padding: 24, borderWidth: 1, borderColor: color.border }, divider: { height: 1, backgroundColor: color.border }, money: { color: color.text, fontWeight: '700', fontSize: 18, fontVariant: ['tabular-nums'], textAlign: 'right' },
  empty: { paddingVertical: 32, paddingHorizontal: 16, alignItems: 'center', gap: 16 }, emptyIcon: { width: 60, height: 60, borderRadius: 20, backgroundColor: color.pale, alignItems: 'center', justifyContent: 'center' },
  nav: { backgroundColor: color.white, borderTopWidth: 1, borderColor: color.border }, navInner: { width: '100%', maxWidth: 580, alignSelf: 'center', flexDirection: 'row', paddingTop: 8, paddingHorizontal: 16 }, navButton: { flex: 1, minHeight: 58, justifyContent: 'center', alignItems: 'center', gap: 5, borderRadius: 12, borderWidth: 2, borderColor: 'transparent' }, navText: { fontSize: 12, fontWeight: '600', color: color.muted },
  item: { paddingVertical: 20, borderBottomWidth: 1, borderColor: color.border, gap: 9 }, itemTitle: { color: color.text, fontSize: 16, fontWeight: '700', lineHeight: 23, flexShrink: 1 }, badge: { fontSize: 12, fontWeight: '600', color: color.primary, backgroundColor: color.pale, paddingVertical: 5, paddingHorizontal: 9, borderRadius: 7, alignSelf: 'flex-start', overflow: 'hidden' }, pager: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8, paddingTop: 16 },
});

/** Fits the written amount without doing financial arithmetic. Very long sums remain scrollable. */
export function MainMoney({ amount, tint = color.primary, baseSize = 36 }: { amount: string; tint?: string; baseSize?: number }) {
  const [width, setWidth] = useState(0);
  const { fontScale } = useWindowDimensions();
  const value = money(amount);
  const units = Array.from(value).reduce((n, c) => n + (/\d/.test(c) ? .66 : c === ',' ? .35 : 1.1), 0);
  const fontSize = width ? Math.max(16, Math.min(baseSize, Math.floor((width - 4) / (units * fontScale)))) : baseSize;
  const overflows = width > 0 && units * fontSize * fontScale > width;
  return <View style={{ width: '100%', gap: 6 }} onLayout={e => setWidth(e.nativeEvent.layout.width)}>
    <ScrollView horizontal showsHorizontalScrollIndicator={overflows} style={{ flexGrow: 0 }} contentContainerStyle={{ alignItems: 'center', minWidth: '100%' }}>
      <Text accessibilityLabel={value} style={{ color: tint, fontSize, fontWeight: '800', fontVariant: ['tabular-nums'], flexShrink: 0, letterSpacing: -.4 }}>{value}</Text>
    </ScrollView>
    {overflows ? <Text style={{ color: tint, opacity: .9, fontSize: 11, lineHeight: 17 }}>금액을 옆으로 밀어 전체를 확인하세요</Text> : null}
  </View>;
}
