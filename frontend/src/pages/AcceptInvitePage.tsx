import { useEffect, useState } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { api, setSession, type AuthUser } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Input, Label } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/components/ui/toast'

interface InvitationInfo {
  id: number
  email: string
  role: string
  expiresAt: string
  accepted: boolean
}

export default function AcceptInvitePage() {
  const { token } = useParams()
  const navigate = useNavigate()
  const { toast } = useToast()
  const [info, setInfo] = useState<InvitationInfo | null>(null)
  const [form, setForm] = useState({ name: '', password: '' })
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api
      .get<InvitationInfo>(`/invitations/${token}`)
      .then(setInfo)
      .catch((e) => toast(e.message || '邀请链接无效', 'error'))
  }, [token])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const res = await api.post<{ accessToken: string; refreshToken: string; user: AuthUser }>(
        '/auth/invitations/accept',
        { token, name: form.name, password: form.password }
      )
      setSession(res.accessToken, res.refreshToken, res.user)
      navigate('/')
    } catch (err) {
      toast(err instanceof Error ? err.message : '接受邀请失败', 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-violet-50 to-white p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>接受团队邀请</CardTitle>
          <CardDescription>
            {info ? <>受邀邮箱：{info.email}</> : '加载中...'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {info && !info.accepted && (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label>姓名</Label>
                <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
              </div>
              <div className="space-y-2">
                <Label>设置密码</Label>
                <Input
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  required
                />
                <p className="text-xs text-muted-foreground">至少 10 位，包含字母、数字和特殊字符</p>
              </div>
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? '提交中...' : '加入团队'}
              </Button>
              <p className="text-center text-sm text-muted-foreground">
                已有账号？
                <Link to="/login" className="text-primary hover:underline ml-1">
                  直接登录
                </Link>
              </p>
            </form>
          )}
          {info?.accepted && (
            <p className="text-center text-muted-foreground py-4">该邀请已被接受。</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
