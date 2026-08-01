import { useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await api.post('/auth/forgot-password', { email })
      setMessage(data.message)
    } catch (err) {
      setError(err.response?.data?.message || '请求失败')
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>找回密码</CardTitle>
        </CardHeader>
        <CardContent>
          {message ? (
            <p className="text-sm">{message}</p>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label>注册邮箱</Label>
                <Input type="email" value={email} required onChange={(e) => setEmail(e.target.value)} />
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button className="w-full">发送重置链接（30分钟有效）</Button>
            </form>
          )}
          <p className="mt-4 text-sm">
            <Link to="/login" className="text-primary hover:underline">返回登录</Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
