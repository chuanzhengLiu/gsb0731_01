import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api, { saveAuth } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'

export default function Register() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', name: '', teamName: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post('/auth/register', form)
      saveAuth(data)
      navigate('/')
    } catch (err) {
      setError(err.response?.data?.message || '注册失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>注册团队（成为管理员）</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-2">
              <Label>团队名称</Label>
              <Input value={form.teamName} required placeholder="如：XX播客工作室"
                onChange={(e) => setForm({ ...form, teamName: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>姓名</Label>
              <Input value={form.name} required
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>邮箱</Label>
              <Input type="email" value={form.email} required
                onChange={(e) => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>密码</Label>
              <Input type="password" value={form.password} required
                placeholder="至少10位，含字母+数字+特殊字符"
                onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button className="w-full" disabled={loading}>{loading ? '注册中…' : '创建团队'}</Button>
          </form>
          <p className="mt-4 text-sm">
            已有账号？<Link to="/login" className="text-primary hover:underline">去登录</Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
