import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'

export default function ResetPassword() {
  const [params] = useSearchParams()
  const [token, setToken] = useState(params.get('token') || '')
  const [password, setPassword] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await api.post('/auth/reset-password', { token, newPassword: password })
      setDone(true)
    } catch (err) {
      setError(err.response?.data?.message || '重置失败')
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>重置密码</CardTitle>
        </CardHeader>
        <CardContent>
          {done ? (
            <p className="text-sm">密码已重置，<Link to="/login" className="text-primary hover:underline">去登录</Link></p>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label>重置 Token</Label>
                <Input value={token} required onChange={(e) => setToken(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>新密码</Label>
                <Input type="password" value={password} required
                  placeholder="至少10位，含字母+数字+特殊字符"
                  onChange={(e) => setPassword(e.target.value)} />
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button className="w-full">重置密码</Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
