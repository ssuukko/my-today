import js from '@eslint/js'
import ts from 'typescript-eslint'
import vue from 'eslint-plugin-vue'
export default ts.config(
  { ignores: ['dist/**', 'node_modules/**', 'test-results/**', 'playwright-report/**'] },
  js.configs.recommended, ...ts.configs.recommended, ...vue.configs['flat/essential'],
  { files: ['src/navigation.ts'], rules: { 'no-control-regex': 'off' } },
  { files: ['**/*.vue'], rules: { 'no-undef': 'off' }, languageOptions: { parserOptions: { parser: ts.parser } } },
)
