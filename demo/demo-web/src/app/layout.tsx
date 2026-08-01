import './globals.css'

export const metadata = {
  title: 'demo 消费方（SSO 参考实现）',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  )
}
