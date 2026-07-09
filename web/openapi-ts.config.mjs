export default {
  input: '../contracts/openapi.json',
  output: 'src/lib/hey-api',
  plugins: [
    '@hey-api/typescript',
    {
      name: '@hey-api/client-fetch',
      runtimeConfigPath: './src/lib/hey-api-runtime.ts',
    },
    '@hey-api/sdk',
  ],
}
