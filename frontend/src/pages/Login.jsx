import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api, { saveAuth } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'

export default function Login() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post('/auth/login', form)
      saveAuth(data)
      navigate('/')
    } catch (err) {
      setError(err.response?.data?.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>登录播客协作系统</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-2">
              <Label>邮箱</Label>
              <Input type="email" value={form.email} required
                onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>密码</Label>
              <Input type="password" value={form.password} required
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button className="w-full" disabled={loading}>{loading ? '登录中…' : '登录'}</Button>
          </form>
          <div className="mt-4 flex justify-between text-sm">
            <Link to="/register" className="text-primary hover:underline">注册团队</Link>
            <Link to="/forgot-password" className="text-muted-foreground hover:underline">忘记密码</Link>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
