import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input, Label } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/components/ui/toast'

export default function RegisterPage() {
  const { register, loading } = useAuth()
  const navigate = useNavigate()
  const { toast } = useToast()
  const [form, setForm] = useState({ email: '', password: '', name: '', teamName: '' })

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, [k]: e.target.value }))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (form.password.length < 10) {
      toast('密码至少 10 位，需包含字母、数字和特殊字符', 'error')
      return
    }
    try {
      await register(form.email, form.password, form.name, form.teamName)
      navigate('/')
    } catch (err) {
      toast(err instanceof Error ? err.message : '注册失败', 'error')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-violet-50 to-white p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>创建账号</CardTitle>
          <CardDescription>注册后将自动为你创建一个团队</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-2">
              <Label>姓名</Label>
              <Input value={form.name} onChange={set('name')} required />
            </div>
            <div className="space-y-2">
              <Label>邮箱</Label>
              <Input type="email" value={form.email} onChange={set('email')} required />
            </div>
            <div className="space-y-2">
              <Label>团队名称（可选）</Label>
              <Input value={form.teamName} onChange={set('teamName')} placeholder="我的播客团队" />
            </div>
            <div className="space-y-2">
              <Label>密码</Label>
              <Input type="password" value={form.password} onChange={set('password')} required />
              <p className="text-xs text-muted-foreground">至少 10 位，包含字母、数字和特殊字符</p>
            </div>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? '注册中...' : '注册'}
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              已有账号？
              <Link to="/login" className="text-primary hover:underline ml-1">
                登录
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
